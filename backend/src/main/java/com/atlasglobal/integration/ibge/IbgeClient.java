package com.atlasglobal.integration.ibge;

import com.atlasglobal.ingestion.ProviderData;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Validates the published table, national geography, dimensions, units and full annual history. */
class IbgeClient {
    private static final int MAX_BODY_BYTES = 1_000_000;
    private final RestClient http;
    private final ObjectMapper mapper;
    private final int attempts;
    private final Duration retryDelay;

    IbgeClient(RestClient http, ObjectMapper mapper, int attempts, Duration retryDelay) {
        this.http = http; this.mapper = mapper; this.attempts = attempts; this.retryDelay = retryDelay;
    }

    record ImportedSeries(ProviderData.Series definition, ProviderData.Indicator metadata, ProviderData.Batch batch) {}

    ImportedSeries series(IbgeCatalog.Entry entry, int from, int to) {
        if (from < 1960 || to < from || to > Year.now(ZoneOffset.UTC).getValue()) {
            throw new IllegalArgumentException("Invalid IBGE annual interval");
        }
        JsonNode metadata = request(URI.create(IbgeConfiguration.BASE_URL + "/" + entry.table() + "/metadados"));
        // Always fetch the table's complete published history: the monetary base is independent of display filters.
        String query = "?localidades=N1%5B1%5D" + (entry.realIncome() ? "&classificacao=1019%5B49243%5D" : "");
        JsonNode data = request(URI.create(IbgeConfiguration.BASE_URL + "/" + entry.table()
            + "/periodos/all/variaveis/" + entry.variable() + query));
        return decode(entry, metadata, data, from, to);
    }

    private JsonNode request(URI uri) {
        for (int attempt = 1; ; attempt++) {
            try {
                var response = http.get().uri(uri).retrieve().toEntity(byte[].class);
                byte[] body = response.getBody();
                if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
                    throw new IbgeException("IBGE returned an empty or oversized response");
                }
                try {
                    if ((body[0] & 0xff) == 0x1f && body.length > 1 && (body[1] & 0xff) == 0x8b) {
                        try (var compressed = new GZIPInputStream(new ByteArrayInputStream(body))) {
                            body = compressed.readNBytes(MAX_BODY_BYTES + 1);
                        }
                        if (body.length > MAX_BODY_BYTES) throw new IbgeException("IBGE returned an oversized response");
                    }
                    return mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(body);
                } catch (IOException ex) { throw new IbgeException("Invalid IBGE JSON response", ex); }
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (attempt >= attempts || (status != 429 && status < 500)) {
                    throw new IbgeException("IBGE HTTP failure: " + status, ex);
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
                        } catch (DateTimeParseException invalid) { /* Use bounded backoff if no valid server delay exists. */ }
                    }
                }
                if (delay > 60_000) throw new IbgeException("IBGE requested a longer retry interval; retry the import later");
                pause(delay);
            } catch (ResourceAccessException ex) {
                if (attempt >= attempts) throw new IbgeException("IBGE connection or timeout failure", ex);
                pause(retryDelay.multipliedBy(attempt).toMillis());
            }
        }
    }

    private static void pause(long delay) {
        try { Thread.sleep(Math.max(0, delay)); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IbgeException("IBGE request interrupted", ex);
        }
    }

    static ImportedSeries decode(IbgeCatalog.Entry entry, JsonNode metadata, JsonNode data, int from, int to) {
        if (metadata == null || !entry.table().equals(metadata.path("id").asText())
            || !"Pesquisa Nacional por Amostra de Domicílios Contínua anual".equals(metadata.path("pesquisa").asText())
            || !"anual".equals(metadata.path("periodicidade").path("frequencia").asText())
            || !metadata.path("nome").asText().startsWith(entry.originalName())) {
            throw new IbgeException("IBGE table or annual survey definition changed");
        }
        JsonNode start = metadata.path("periodicidade").path("inicio"), end = metadata.path("periodicidade").path("fim");
        if (!start.isIntegralNumber() || !end.isIntegralNumber() || start.intValue() < 1960
            || end.intValue() < start.intValue() || end.intValue() > Year.now(ZoneOffset.UTC).getValue()) {
            throw new IbgeException("Invalid IBGE published annual interval");
        }
        if (!metadata.path("nivelTerritorial").path("Administrativo").isArray()) {
            throw new IbgeException("Missing IBGE national geography metadata");
        }
        boolean national = false, variable = false;
        for (JsonNode level : metadata.path("nivelTerritorial").path("Administrativo")) national |= "N1".equals(level.asText());
        for (JsonNode candidate : metadata.path("variaveis")) {
            if (!entry.variable().equals(candidate.path("id").asText())) continue;
            if (variable || !entry.originalName().equals(candidate.path("nome").asText())
                || !entry.sourceUnit().equals(candidate.path("unidade").asText())) {
                throw new IbgeException("IBGE variable definition or unit changed");
            }
            variable = true;
        }
        if (!national || !variable) throw new IbgeException("Missing IBGE selected variable or national geography");
        verifyMetadataClassifications(entry, metadata.path("classificacoes"));
        if (data == null || !data.isArray() || data.size() != 1) throw new IbgeException("Unexpected IBGE variable response");
        JsonNode response = data.get(0);
        if (!entry.variable().equals(response.path("id").asText())
            || !entry.originalName().equals(response.path("variavel").asText())
            || !entry.sourceUnit().equals(response.path("unidade").asText())) {
            throw new IbgeException("IBGE returned an incompatible variable or unit");
        }
        JsonNode results = response.path("resultados");
        if (!results.isArray() || results.size() != 1) throw new IbgeException("Unexpected IBGE classification results");
        JsonNode result = results.get(0);
        verifyResponseClassifications(entry, result.path("classificacoes"));
        JsonNode series = result.path("series");
        if (!series.isArray() || series.size() != 1) throw new IbgeException("Unexpected IBGE geography series");
        JsonNode local = series.get(0).path("localidade");
        if (!"1".equals(local.path("id").asText()) || !"N1".equals(local.path("nivel").path("id").asText())
            || !"Brasil".equals(local.path("nome").asText())) {
            throw new IbgeException("IBGE returned a non-Brazil geography");
        }
        JsonNode values = series.get(0).path("serie");
        if (!values.isObject() || values.isEmpty()) throw new IbgeException("IBGE returned no annual observations");
        var observations = new ArrayList<ProviderData.Observation>();
        Set<Integer> years = new HashSet<>();
        values.fields().forEachRemaining(cell -> {
            if (!cell.getKey().matches("[12][0-9]{3}")) throw new IbgeException("Nonannual IBGE observation");
            int year = Integer.parseInt(cell.getKey());
            if (year < start.intValue() || year > end.intValue() || !years.add(year)) {
                throw new IbgeException("IBGE observation outside its published interval");
            }
            if (!cell.getValue().isTextual()) throw new IbgeException("Invalid IBGE observation representation");
            String raw = cell.getValue().textValue();
            BigDecimal value = switch (raw) {
                case "-" -> BigDecimal.ZERO; // SIDRA absolute zero, distinct from missing symbols.
                case "..", "...", "X" -> null;
                default -> number(raw);
            };
            if (value != null && (value.signum() < 0 || (!entry.realIncome() && value.compareTo(BigDecimal.ONE) > 0))) {
                throw new IbgeException("IBGE value outside the selected measure's range");
            }
            if (year >= from && year <= to) {
                observations.add(new ProviderData.Observation("BRA", entry.code(), year, value,
                    Set.of("-", "..", "...", "X").contains(raw) ? raw : null));
            }
        });
        if (years.stream().mapToInt(Integer::intValue).max().orElseThrow() != end.intValue()) {
            throw new IbgeException("IBGE metadata and latest published observation disagree");
        }
        observations.sort(java.util.Comparator.comparingInt(ProviderData.Observation::year));
        return new ImportedSeries(entry.definition(end.intValue()), entry.metadata(end.intValue()),
            new ProviderData.Batch(observations, null));
    }

    private static BigDecimal number(String raw) {
        if (!raw.matches("-?[0-9]+(?:\\.[0-9]+)?")) throw new IbgeException("Invalid IBGE numeric observation");
        try { return new BigDecimal(raw); }
        catch (NumberFormatException ex) { throw new IbgeException("Invalid IBGE numeric observation", ex); }
    }

    private static void verifyMetadataClassifications(IbgeCatalog.Entry entry, JsonNode classifications) {
        if (!classifications.isArray() || classifications.size() != (entry.realIncome() ? 1 : 0)) {
            throw new IbgeException("IBGE classification metadata changed");
        }
        if (!entry.realIncome()) return;
        JsonNode classification = classifications.get(0);
        if (!"1019".equals(classification.path("id").asText())) throw new IbgeException("IBGE income classification changed");
        boolean total = false;
        for (JsonNode category : classification.path("categorias")) {
            if ("49243".equals(category.path("id").asText()) && "Total".equals(category.path("nome").asText())) total = true;
        }
        if (!total) throw new IbgeException("Missing IBGE total population category");
    }

    private static void verifyResponseClassifications(IbgeCatalog.Entry entry, JsonNode classifications) {
        if (!classifications.isArray() || classifications.size() != (entry.realIncome() ? 1 : 0)) {
            throw new IbgeException("Unexpected IBGE response classifications");
        }
        if (!entry.realIncome()) return;
        JsonNode classification = classifications.get(0), categories = classification.path("categoria");
        if (!"1019".equals(classification.path("id").asText()) || !categories.isObject()
            || categories.size() != 1 || !"Total".equals(categories.path("49243").asText())) {
            throw new IbgeException("IBGE returned a different population category");
        }
    }
}
