package com.atlasglobal.integration.imf;

import com.atlasglobal.ingestion.AnnualDataProvider;
import com.atlasglobal.ingestion.ImportProperties;
import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.integration.RateHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Native IMF metadata determines the edition and projection boundary; no calendar heuristic labels actuals. */
@Service
public class ImfProvider implements AnnualDataProvider {
    private static final String BASE = "https://www.imf.org/external/datamapper/api/v2/";
    private final RateHttp http;

    @Autowired
    public ImfProvider(ObjectMapper mapper) { this(RateHttp.create(mapper, "application/json")); }
    ImfProvider(RateHttp http) { this.http = http; }

    public ProviderData.Source source() { return ImfCatalog.SOURCE; }
    public long lockKey() { return 728301952153L; }

    public List<ProviderData.ImportedSeries> load(ImportProperties settings) {
        JsonNode metadata = http.get(URI.create(BASE + "indicators"));
        JsonNode countries = http.get(URI.create(BASE + "countries"));
        validateVersion(metadata);
        validateVersion(countries);
        if (!metadata.path("indicators").isObject() || !countries.path("countries").isObject()) throw invalid("catalog");
        String periods = IntStream.rangeClosed(settings.fromYear(), settings.endYear()).mapToObj(Integer::toString)
            .collect(Collectors.joining(","));
        var result = new ArrayList<ProviderData.ImportedSeries>();
        for (var entry : ImfCatalog.ENTRIES) {
            JsonNode expected = metadata.path("indicators").path(entry.externalCode());
            validateMetadata(entry, expected);
            JsonNode response = http.get(URI.create(BASE + entry.externalCode() + "?periods=" + periods));
            result.add(decode(entry, expected, countries.path("countries"), response, settings));
        }
        return List.copyOf(result);
    }

    static ProviderData.ImportedSeries decode(ImfCatalog.Entry entry, JsonNode expected, JsonNode countries,
                                             JsonNode response, ImportProperties settings) {
        validateVersion(response);
        String edition = validateMetadata(entry, expected);
        JsonNode embedded = response.path("indicators").path(entry.externalCode());
        if (!expected.equals(embedded)) throw invalid("metadata changed during import");
        JsonNode values = response.path("values").path(entry.externalCode());
        if (!values.isObject() || values.size() > 500) throw invalid("country collection");
        int projectionYear = expected.path("projection-year").intValue();
        var observations = new ArrayList<ProviderData.Observation>();
        for (String country : settings.countries()) {
            String externalCountry = country.equals("XKX") ? "UVK" : country;
            JsonNode identity = countries.path(externalCountry);
            if (identity.isMissingNode()) continue; // A valid Atlas country can be outside IMF coverage.
            if (!identity.path("label").isTextual() || identity.path("label").asText().isBlank()) throw invalid("country identity");
            JsonNode periods = values.get(externalCountry);
            if (periods == null) continue;
            if (!periods.isObject() || periods.size() > 300) throw invalid("annual collection");
            for (var fields = periods.fields(); fields.hasNext();) {
                var field = fields.next();
                if (!field.getKey().matches("[0-9]{4}")) throw invalid("annual period");
                int year = Integer.parseInt(field.getKey());
                if (year < 1900 || year > 2200) throw invalid("annual period");
                if (year < settings.fromYear() || year > settings.endYear()) continue;
                JsonNode raw = field.getValue();
                if (!raw.isNull() && !raw.isNumber()) throw invalid("numeric value");
                BigDecimal value = raw.isNull() ? null : raw.decimalValue();
                observations.add(new ProviderData.Observation(country, entry.code(), year, value,
                    year >= projectionYear ? "IMF_PROJECTION" : "IMF_HISTORICAL"));
            }
        }
        observations.sort(java.util.Comparator.comparing(ProviderData.Observation::countryCode)
            .thenComparingInt(ProviderData.Observation::year));
        String method = expected.path("description").asText() + "\nEdition: " + expected.path("source").asText()
            + ". The IMF identifies " + projectionYear + " as the first projection year. Records from that year onward "
            + "retain IMF_PROJECTION status; earlier records retain IMF_HISTORICAL status, not a claim of final observed data. "
            + "WEO historical series can include IMF staff estimates and adjustments for structural breaks. "
            + "Fiscal coverage, accounting conventions and national definitions may differ between countries. "
            + "These IMF definitions are not spliced into World Bank series. "
            + "https://www.imf.org/external/datamapper/datasets/WEO";
        var definition = new ProviderData.Series(entry.code(), entry.name(), "ECONOMY", "PERCENT_GDP", "WEO:" + edition);
        var description = new ProviderData.Indicator(entry.externalCode(), expected.path("label").asText(), method,
            "International Monetary Fund — World Economic Outlook");
        // Indicator metadata maintenance is not validated as the revision date of every observation.
        return new ProviderData.ImportedSeries(definition, description, new ProviderData.Batch(observations, null));
    }

    private static String validateMetadata(ImfCatalog.Entry entry, JsonNode metadata) {
        if (!entry.originalName().equals(metadata.path("label").asText()) || !"WEO".equals(metadata.path("dataset").asText())
            || !"Percent of GDP".equals(metadata.path("unit").asText()) || !metadata.path("description").isTextual()
            || metadata.path("description").asText().isBlank() || !metadata.path("projection-year").isIntegralNumber()) {
            throw invalid("indicator definition, unit or projection boundary");
        }
        String source = metadata.path("source").asText();
        if (!source.matches("World Economic Outlook \\([A-Za-z]+ [0-9]{4}\\)")) throw invalid("WEO edition");
        YearMonth edition;
        try {
            edition = YearMonth.parse(source.substring("World Economic Outlook (".length(), source.length() - 1),
                DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH));
        } catch (RuntimeException ex) { throw invalid("WEO edition"); }
        if (edition.getYear() < 1900 || edition.isAfter(YearMonth.now(ZoneOffset.UTC))
            || metadata.path("projection-year").intValue() != edition.getYear()) throw invalid("WEO projection boundary");
        return edition.toString();
    }

    private static void validateVersion(JsonNode root) {
        if (!root.isObject() || !"2".equals(root.path("api").path("version").asText())) throw invalid("API version");
    }
    private static IllegalStateException invalid(String detail) { return new IllegalStateException("Invalid IMF response: " + detail); }
}
