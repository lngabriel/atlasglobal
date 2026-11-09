package com.atlasglobal.integration.oecd;

import com.atlasglobal.ingestion.ProviderData;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class OecdClient {
    private final RestClient http;
    private final int attempts;
    private final Duration retryDelay;

    OecdClient(RestClient http, int attempts, Duration retryDelay) {
        this.http = http; this.attempts = attempts; this.retryDelay = retryDelay;
    }

    record ImportedSeries(ProviderData.Series definition, ProviderData.Indicator metadata, ProviderData.Batch batch) {}

    List<ImportedSeries> series(OecdCatalog.Entry entry, List<String> countries, int from, int to) {
        if (countries.isEmpty() || countries.size() > 50 || countries.stream().anyMatch(code -> !code.matches("[A-Z0-9]{3}"))
            || from < 1960 || to < from || to > 2200) {
            throw new IllegalArgumentException("Invalid OECD observation request");
        }
        ResponseEntity<String> response = request(entry, from, to);
        return decode(entry, response.getBody(), Set.copyOf(countries), from, to, sourceDate(response.getHeaders()));
    }

    private ResponseEntity<String> request(OecdCatalog.Entry entry, int from, int to) {
        for (int attempt = 1; ; attempt++) {
            try {
                return http.get().uri(builder -> builder
                    .path("/data/" + entry.agency() + "," + entry.dataset() + ",1.0/" + entry.key())
                    .queryParam("startPeriod", from).queryParam("endPeriod", to)
                    .queryParam("format", "csvfile").build())
                    .retrieve().toEntity(String.class);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (attempt >= attempts || (status != 429 && status < 500)) {
                    throw new OecdException("OECD HTTP failure: " + status, ex);
                }
                long delay = retryDelay.multipliedBy(attempt).toMillis();
                String retryAfter = ex.getResponseHeaders() == null ? null
                    : ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                if (retryAfter != null) {
                    try { delay = Math.max(delay, Math.multiplyExact(Long.parseLong(retryAfter), 1000)); }
                    catch (NumberFormatException | ArithmeticException ignored) {
                        try {
                            Instant retryAt = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                            delay = Math.max(delay, Duration.between(Instant.now(), retryAt).toMillis());
                        } catch (DateTimeParseException invalid) { /* Fall back to bounded backoff. */ }
                    }
                }
                if (delay > 60_000) throw new OecdException("OECD requested a longer retry interval; retry the import later");
                pause(delay);
            } catch (ResourceAccessException ex) {
                if (attempt >= attempts) throw new OecdException("OECD connection or timeout failure", ex);
                pause(retryDelay.multipliedBy(attempt).toMillis());
            }
        }
    }

    private static void pause(long delay) {
        try { Thread.sleep(Math.max(0, delay)); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OecdException("OECD request interrupted", ex);
        }
    }

    private static LocalDate sourceDate(HttpHeaders headers) {
        String modified = headers.getFirst(HttpHeaders.LAST_MODIFIED);
        if (modified == null) return null;
        try { return ZonedDateTime.parse(modified, DateTimeFormatter.RFC_1123_DATE_TIME).withZoneSameInstant(ZoneOffset.UTC).toLocalDate(); }
        catch (DateTimeParseException ex) { throw new OecdException("Invalid OECD source modification date", ex); }
    }

    static List<ImportedSeries> decode(OecdCatalog.Entry entry, String body, Set<String> countries,
                                       int from, int to, LocalDate updatedAt) {
        List<Map<String, String>> rows = OecdCsv.records(body);
        if (rows.isEmpty()) throw new OecdException("OECD returned no dataset records");
        Set<String> required = new HashSet<>(List.of("DATAFLOW", "REF_AREA", "MEASURE", "UNIT_MEASURE",
            "TIME_PERIOD", "OBS_VALUE", "OBS_STATUS", "UNIT_MULT"));
        required.addAll(entry.ppp() ? List.of("PAY_PERIOD", "PRICE_BASE", "AGGREGATION_OPERATION", "SEX", "BASE_PER")
            : List.of("FREQ", "CHAPTER", "PRICE_BASE", "TRANSFORMATION", "SECTOR"));
        if (!rows.getFirst().keySet().containsAll(required)) throw new OecdException("Missing OECD CSV dimensions");
        List<Map<String, String>> matching = new ArrayList<>();
        Set<String> bases = new TreeSet<>();
        for (Map<String, String> row : rows) {
            if (!entry.dataflow().equals(row.get("DATAFLOW"))) throw new OecdException("Unexpected OECD dataflow");
            if (!matches(entry, row)) continue;
            matching.add(row);
            if (entry.ppp() && !row.get("BASE_PER").isBlank()) {
                String base = row.get("BASE_PER");
                if (!base.matches("[12][0-9]{3}")) throw new OecdException("Invalid OECD purchasing-power base year");
                bases.add(base);
            }
        }
        if (matching.isEmpty()) throw new OecdException("OECD response does not contain the selected methodology");
        if (!entry.ppp()) bases.add("");
        if (bases.isEmpty()) throw new OecdException("Missing OECD purchasing-power base year");
        Map<String, List<ProviderData.Observation>> groups = new HashMap<>();
        for (String base : bases) groups.put(base, new ArrayList<>());
        Set<String> observed = new HashSet<>();
        for (Map<String, String> row : matching) {
            String country = row.get("REF_AREA");
            if (!countries.contains(country)) continue;
            if (!row.get("TIME_PERIOD").matches("[12][0-9]{3}")) throw new OecdException("Nonannual OECD observation");
            int year = Integer.parseInt(row.get("TIME_PERIOD"));
            if (year < from || year > to) throw new OecdException("OECD returned an observation outside the requested interval");
            String base = entry.ppp() ? row.get("BASE_PER") : "";
            if (entry.ppp() && base.isBlank()) {
                // OECD omits BASE_PER on some missing rows. A single explicit dataset base can identify them.
                if (!row.get("OBS_VALUE").isBlank() || bases.size() != 1) {
                    throw new OecdException("Ambiguous OECD purchasing-power base year");
                }
                base = bases.iterator().next();
            }
            String key = country + ":" + year + ":" + base;
            if (!observed.add(key)) throw new OecdException("Duplicate OECD country, year and methodology");
            String status = row.get("OBS_STATUS");
            if (status.length() > 40) throw new OecdException("Invalid OECD observation status");
            BigDecimal value = null;
            if (!row.get("OBS_VALUE").isBlank()) {
                try {
                    int multiplier = Integer.parseInt(row.get("UNIT_MULT"));
                    if (Math.abs(multiplier) > 12) throw new NumberFormatException("Unsupported scale");
                    value = new BigDecimal(row.get("OBS_VALUE")).scaleByPowerOfTen(multiplier);
                } catch (NumberFormatException | ArithmeticException ex) {
                    throw new OecdException("Invalid OECD numeric observation or unit multiplier", ex);
                }
            }
            groups.get(base).add(new ProviderData.Observation(country, entry.code(), year, value, status));
        }
        List<ImportedSeries> result = new ArrayList<>();
        for (String base : bases) {
            result.add(new ImportedSeries(entry.definition(base), entry.metadata(base),
                new ProviderData.Batch(groups.get(base), updatedAt)));
        }
        return List.copyOf(result);
    }

    private static boolean matches(OecdCatalog.Entry entry, Map<String, String> row) {
        if (!entry.ppp()) return "A".equals(row.get("FREQ")) && "LES1M_FD4".equals(row.get("MEASURE"))
            && "PT_B6N_S1M".equals(row.get("UNIT_MEASURE")) && "NAAG_V".equals(row.get("CHAPTER"))
            && "V".equals(row.get("PRICE_BASE")) && "N".equals(row.get("TRANSFORMATION")) && "S1M".equals(row.get("SECTOR"));
        boolean average = entry.kind() == OecdCatalog.Kind.AVERAGE_WAGE;
        return (average ? "WG" : "SM_WG").equals(row.get("MEASURE"))
            && "USD_PPP".equals(row.get("UNIT_MEASURE")) && "A".equals(row.get("PAY_PERIOD"))
            && "Q".equals(row.get("PRICE_BASE")) && "_Z".equals(row.get("SEX"))
            && (average ? "MEAN" : "_Z").equals(row.get("AGGREGATION_OPERATION"));
    }
}
