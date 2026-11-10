package com.atlasglobal.integration.bcb;

import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.policyrate.PolicyRateData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class BcbClient {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);
    private final RestClient http;
    private final ObjectMapper mapper;
    private final int attempts;
    private final Duration retryDelay;
    private final Clock clock;

    BcbClient(RestClient http, ObjectMapper mapper, int attempts, Duration retryDelay, Clock clock) {
        this.http = http; this.mapper = mapper; this.attempts = attempts;
        this.retryDelay = retryDelay; this.clock = clock;
    }

    ProviderData.Batch series(int from, int to) {
        if (from < 1960 || to < from || to > 2200) throw new IllegalArgumentException("Invalid BCB year interval");
        int end = Math.min(to, Year.now(clock).getValue() - 1);
        List<ProviderData.Observation> result = new ArrayList<>();
        // Monthly SGS 29037 starts in 2005. Small bounded requests also avoid very large responses.
        for (int first = Math.max(2005, from); first <= end; first += 10) {
            int last = Math.min(first + 9, end);
            String body = request(29037, LocalDate.of(first, 1, 1), LocalDate.of(last, 12, 31));
            result.addAll(decode(body, first, last));
        }
        result.sort(Comparator.comparingInt(ProviderData.Observation::year));
        // Neither the HTTP resource date nor the catalog metadata date validates a statistical revision.
        return new ProviderData.Batch(result, null);
    }

    List<PolicyRateData.Point> policyRate(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || to.isAfter(LocalDate.now(clock))) throw new IllegalArgumentException("Invalid BCB policy-rate interval");
        var points = new ArrayList<PolicyRateData.Point>();
        for (LocalDate start = from.isBefore(LocalDate.of(1999, 1, 1)) ? LocalDate.of(1999, 1, 1) : from;
             !start.isAfter(to); start = start.plusYears(10)) {
            LocalDate end = start.plusYears(10).minusDays(1).isBefore(to) ? start.plusYears(10).minusDays(1) : to;
            JsonNode rows = readRows(request(432, start, end));
            var dates = new HashSet<LocalDate>();
            for (JsonNode row : rows) {
                LocalDate date;
                try { date = LocalDate.parse(row.path("data").asText(), DATE); }
                catch (DateTimeParseException ex) { throw new BcbException("Invalid Selic observation date", ex); }
                if (date.isBefore(start) || date.isAfter(end) || !dates.add(date) || !row.has("valor")) {
                    throw new BcbException("Duplicate or out-of-scope Selic observation");
                }
                JsonNode value = row.get("valor");
                if (!value.isNull() && (!value.isTextual() || !value.textValue().matches("-?[0-9]+(?:\\.[0-9]+)?"))) {
                    throw new BcbException("Invalid Selic rate");
                }
                points.add(new PolicyRateData.Point(date, value.isNull() ? null : new BigDecimal(value.textValue()), null));
            }
        }
        points.sort(Comparator.comparing(PolicyRateData.Point::date));
        return List.copyOf(points);
    }

    private String request(int series, LocalDate from, LocalDate to) {
        for (int attempt = 1; ; attempt++) {
            try {
                return http.get().uri(builder -> builder.path("/dados/serie/bcdata.sgs." + series + "/dados")
                    .queryParam("formato", "json").queryParam("dataInicial", DATE.format(from))
                    .queryParam("dataFinal", DATE.format(to)).build()).retrieve().body(String.class);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (attempt >= attempts || (status != 429 && status < 500)) {
                    throw new BcbException("BCB HTTP failure: " + status, ex);
                }
                long delay = retryDelay.multipliedBy(attempt).toMillis();
                String retryAfter = ex.getResponseHeaders() == null ? null
                    : ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                if (retryAfter != null) {
                    try { delay = Math.max(delay, Math.multiplyExact(Long.parseLong(retryAfter), 1000)); }
                    catch (NumberFormatException | ArithmeticException ignored) {
                        try {
                            Instant retryAt = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                            delay = Math.max(delay, Duration.between(clock.instant(), retryAt).toMillis());
                        } catch (DateTimeParseException invalid) { /* Use the bounded default backoff. */ }
                    }
                }
                if (delay > 60_000) throw new BcbException("BCB requested a longer retry interval; retry the import later");
                pause(delay);
            } catch (ResourceAccessException ex) {
                if (attempt >= attempts) throw new BcbException("BCB connection or timeout failure", ex);
                pause(retryDelay.multipliedBy(attempt).toMillis());
            }
        }
    }

    private static void pause(long milliseconds) {
        try { Thread.sleep(Math.max(0, milliseconds)); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BcbException("BCB request interrupted", ex);
        }
    }

    List<ProviderData.Observation> decode(String body, int from, int to) {
        JsonNode rows = readRows(body);
        var observed = new HashSet<LocalDate>();
        var result = new ArrayList<ProviderData.Observation>();
        for (JsonNode row : rows) {
            if (!row.isObject() || !row.path("data").isTextual() || !row.has("valor")) {
                throw new BcbException("Missing BCB observation fields");
            }
            LocalDate date;
            try { date = LocalDate.parse(row.get("data").textValue(), DATE); }
            catch (DateTimeParseException ex) { throw new BcbException("Invalid BCB observation date", ex); }
            if (date.getDayOfMonth() != 1 || date.getYear() < from || date.getYear() > to) {
                throw new BcbException("BCB observation outside the requested monthly interval");
            }
            if (!observed.add(date)) throw new BcbException("Duplicate BCB monthly observation");
            BigDecimal value = null;
            JsonNode reported = row.get("valor");
            if (!reported.isNull()) {
                if (!reported.isTextual() || !reported.textValue().matches("[0-9]+(?:\\.[0-9]+)?")) {
                    throw new BcbException("Invalid BCB numeric observation");
                }
                value = new BigDecimal(reported.textValue());
            }
            if (date.getMonthValue() == 12) {
                result.add(new ProviderData.Observation("BRA", BcbCatalog.DEFINITION.code(), date.getYear(), value, null));
            }
        }
        return List.copyOf(result);
    }

    private JsonNode readRows(String body) {
        if (body == null || body.length() > 1_000_000) throw new BcbException("Invalid BCB response size");
        JsonNode rows;
        try { rows = mapper.readTree(body); }
        catch (JsonProcessingException ex) { throw new BcbException("Invalid BCB JSON response", ex); }
        if (rows == null || !rows.isArray() || rows.size() > 5000) throw new BcbException("Invalid BCB observation collection");
        return rows;
    }
}
