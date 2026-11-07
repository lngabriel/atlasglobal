package com.atlasglobal.integration.worldbank;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class WorldBankClient {
    private final RestClient http;
    private final ObjectMapper mapper;
    private final WorldBankProperties settings;

    WorldBankClient(RestClient http, ObjectMapper mapper, WorldBankProperties settings) {
        this.http = http;
        this.mapper = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.settings = settings;
    }

    record Page<T>(int pages, int total, String sourceId, LocalDate updatedAt, List<T> records) {}

    List<WorldBankDtos.Country> countries() {
        var result = new ArrayList<WorldBankDtos.Country>();
        pages("/country", Map.of(), WorldBankDtos.Country.class, p -> result.addAll(p.records()));
        return List.copyOf(result);
    }

    WorldBankDtos.Indicator indicator(String code) {
        validateIndicator(code);
        var result = new ArrayList<WorldBankDtos.Indicator>();
        pages("/indicator/" + code, Map.of("source", "2"), WorldBankDtos.Indicator.class,
            p -> result.addAll(p.records()));
        if (result.size() != 1 || !code.equals(result.getFirst().id())
            || result.getFirst().source() == null || !"2".equals(result.getFirst().source().id())) {
            throw new WorldBankException("World Bank indicator not found in source 2: " + code);
        }
        return result.getFirst();
    }

    void observations(List<String> countries, String indicator, int from, int to,
                      Consumer<Page<WorldBankDtos.Observation>> consumer) {
        validateIndicator(indicator);
        if (countries.isEmpty() || countries.size() > 300 || countries.stream().anyMatch(c -> !c.matches("[A-Z0-9]{3}"))
            || from < 1960 || to < from || to > 2200) {
            throw new IllegalArgumentException("Invalid World Bank observation request");
        }
        pages("/country/" + String.join(";", countries) + "/indicator/" + indicator,
            Map.of("source", "2", "date", from + ":" + to), WorldBankDtos.Observation.class, consumer);
    }

    private static void validateIndicator(String code) {
        if (code == null || !code.matches("[A-Z0-9.]{1,100}")) {
            throw new IllegalArgumentException("Invalid World Bank indicator code");
        }
    }

    private <T> void pages(String path, Map<String, String> params, Class<T> type, Consumer<Page<T>> consumer) {
        int totalPages = 1;
        int expectedTotal = -1;
        int seen = 0;
        LocalDate updatedAt = null;
        for (int page = 1; page <= totalPages; page++) {
            int requestedPage = page;
            String body = request(path, params, requestedPage);
            Page<T> result = decode(body, type, requestedPage);
            if (page > 1 && (totalPages != result.pages() || expectedTotal != result.total()
                || !java.util.Objects.equals(updatedAt, result.updatedAt()))) {
                throw new WorldBankException("World Bank pagination changed during import; retry the import");
            }
            totalPages = result.pages();
            expectedTotal = result.total();
            updatedAt = result.updatedAt();
            seen += result.records().size();
            consumer.accept(result);
        }
        if (seen != expectedTotal) throw new WorldBankException("Incomplete World Bank response");
    }

    private String request(String path, Map<String, String> params, int page) {
        for (int attempt = 1; ; attempt++) {
            try {
                return http.get().uri(builder -> {
                    builder.path(path).queryParam("format", "json").queryParam("per_page", settings.pageSize())
                        .queryParam("page", page);
                    params.forEach(builder::queryParam);
                    return builder.build();
                }).retrieve().body(String.class);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (attempt >= settings.attempts() || (status != 429 && status < 500)) {
                    throw new WorldBankException("World Bank HTTP failure: " + status, ex);
                }
                long delay = settings.retryDelay().multipliedBy(attempt).toMillis();
                String retryAfter = ex.getResponseHeaders() == null ? null
                    : ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                if (retryAfter != null) {
                    try { delay = Math.max(delay, Math.multiplyExact(Long.parseLong(retryAfter), 1000)); }
                    catch (NumberFormatException | ArithmeticException ignored) {
                        try {
                            var retryAt = java.time.ZonedDateTime.parse(retryAfter,
                                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                            delay = Math.max(delay, java.time.Duration.between(java.time.Instant.now(), retryAt).toMillis());
                        } catch (java.time.format.DateTimeParseException invalid) { /* Use bounded backoff. */ }
                    }
                }
                if (delay > 60_000) {
                    throw new WorldBankException("World Bank requested a longer retry interval; retry the import later");
                }
                pause(delay);
            } catch (ResourceAccessException ex) {
                if (attempt >= settings.attempts()) {
                    throw new WorldBankException("World Bank connection or timeout failure", ex);
                }
                pause(settings.retryDelay().multipliedBy(attempt).toMillis());
            }
        }
    }

    private void pause(long millis) {
        try { Thread.sleep(Math.max(0, millis)); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WorldBankException("World Bank request interrupted", ex);
        }
    }

    private <T> Page<T> decode(String body, Class<T> type, int page) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isArray() || root.size() != 2 || root.get(0).has("message")) {
                throw new WorldBankException("Unexpected World Bank response or provider error");
            }
            JsonNode meta = root.get(0);
            int pages = meta.path("pages").asInt(-1);
            int total = meta.path("total").asInt(-1);
            if (meta.path("page").asInt(-1) != page || pages < 0 || pages > 10000 || total < 0
                || (pages == 0 && meta.path("total").asInt(-1) != 0)) {
                throw new WorldBankException("Invalid World Bank pagination metadata");
            }
            List<T> rows = new ArrayList<>();
            if (!root.get(1).isNull()) {
                if (!root.get(1).isArray()) throw new WorldBankException("Invalid World Bank data rows");
                for (JsonNode row : root.get(1)) rows.add(mapper.treeToValue(row, type));
            }
            String updated = meta.path("lastupdated").asText("");
            return new Page<>(pages, total, meta.path("sourceid").asText(""),
                updated.isBlank() ? null : LocalDate.parse(updated), List.copyOf(rows));
        } catch (WorldBankException ex) { throw ex; }
        catch (Exception ex) { throw new WorldBankException("Could not decode World Bank response", ex); }
    }
}
