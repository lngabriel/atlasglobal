package com.atlasglobal.integration.bis;

import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.integration.RateHttp;
import com.atlasglobal.policyrate.PolicyRateData;
import com.atlasglobal.policyrate.PolicyRateProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BisPolicyRateProvider implements PolicyRateProvider {
    static final String BASE = "https://stats.bis.org/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/D.";
    private final RateHttp http;
    @Autowired public BisPolicyRateProvider(ObjectMapper mapper) {
        this(RateHttp.create(mapper, "application/vnd.sdmx.data+json;version=1.0.0"));
    }
    BisPolicyRateProvider(RateHttp http) { this.http = http; }
    public ProviderData.Source source() {
        return new ProviderData.Source("BIS", "Bank for International Settlements", "https://stats.bis.org/api/v2/",
            "Central bank policy rates — WS_CBPOL", "https://data.bis.org/topics/CBPOL");
    }
    public long lockKey() { return 728301952151L; }
    public List<PolicyRateData.Series> load(LocalDate from, LocalDate to) {
        String filter = URLEncoder.encode("ge:" + from + "+le:" + to, StandardCharsets.UTF_8);
        return decode(http.get(URI.create(BASE + "?c%5BTIME_PERIOD%5D=" + filter)), from, to);
    }

    static List<PolicyRateData.Series> decode(JsonNode response, LocalDate from, LocalDate to) {
        JsonNode data = response.path("data"), datasets = data.path("dataSets"), structure = data.path("structure");
        if (!datasets.isArray() || datasets.size() != 1) throw invalid("dataset");
        JsonNode dataset = datasets.get(0);
        boolean correctFlow = false;
        for (JsonNode link : dataset.path("links")) correctFlow |= "urn:sdmx:org.sdmx.infomodel.datastructure.Dataflow=BIS:WS_CBPOL(1.0)".equals(link.path("urn").asText());
        JsonNode dimensions = structure.path("dimensions").path("series");
        JsonNode timeDimensions = structure.path("dimensions").path("observation");
        if (!correctFlow || !dimensions.isArray() || dimensions.size() != 2
            || !"FREQ".equals(dimensions.get(0).path("id").asText())
            || !"REF_AREA".equals(dimensions.get(1).path("id").asText())
            || dimensions.get(0).path("values").size() != 1
            || !"D".equals(dimensions.get(0).path("values").get(0).path("id").asText())
            || !timeDimensions.isArray() || timeDimensions.size() != 1
            || !"TIME_PERIOD".equals(timeDimensions.get(0).path("id").asText())) throw invalid("daily dimensions");
        JsonNode attrs = structure.path("attributes");
        if (!"368".equals(attribute(attrs.path("dataset"), dataset.path("attributes"), "UNIT_MEASURE"))) throw invalid("unit");
        int multiplier;
        try { multiplier = Integer.parseInt(attribute(attrs.path("dataset"), dataset.path("attributes"), "UNIT_MULT")); }
        catch (RuntimeException ex) { throw invalid("unit multiplier"); }
        if (Math.abs(multiplier) > 12) throw invalid("unit multiplier");
        JsonNode periods = timeDimensions.get(0).path("values"), areas = dimensions.get(1).path("values");
        if (!periods.isArray() || periods.size() > 40_000 || !areas.isArray() || areas.size() > 200
            || !dataset.path("series").isObject()) throw invalid("collection size");
        var result = new ArrayList<PolicyRateData.Series>();
        var seenAreas = new HashSet<String>();
        for (var fields = dataset.path("series").fields(); fields.hasNext();) {
            var field = fields.next();
            if (!field.getKey().matches("0:[0-9]+")) throw invalid("series key");
            int areaIndex = Integer.parseInt(field.getKey().substring(2));
            if (areaIndex >= areas.size()) throw invalid("area index");
            String area = areas.get(areaIndex).path("id").asText();
            if (!area.matches("[A-Z]{2}") || !seenAreas.add(area)) throw invalid("reference area");
            JsonNode series = field.getValue(), seriesAttrs = series.path("attributes");
            String title = attribute(attrs.path("series"), seriesAttrs, "TITLE");
            String compilation = attribute(attrs.path("series"), seriesAttrs, "COMPILATION");
            String organization = attribute(attrs.path("series"), seriesAttrs, "SOURCE_REF");
            String breaks = attribute(attrs.path("series"), seriesAttrs, "SUPP_INFO_BREAKS");
            if (title.isBlank() || compilation.isBlank() || organization.isBlank() || !series.path("observations").isObject()) throw invalid("methodology");
            var points = new ArrayList<PolicyRateData.Point>();
            var seenDates = new HashSet<LocalDate>();
            for (var entries = series.path("observations").fields(); entries.hasNext();) {
                var entry = entries.next();
                int timeIndex;
                try { timeIndex = Integer.parseInt(entry.getKey()); }
                catch (NumberFormatException ex) { throw invalid("date index"); }
                if (timeIndex < 0 || timeIndex >= periods.size() || !entry.getValue().isArray() || entry.getValue().isEmpty()) throw invalid("observation");
                LocalDate date = LocalDate.parse(periods.get(timeIndex).path("id").asText());
                if (date.isBefore(from) || date.isAfter(to) || !seenDates.add(date)) throw invalid("date outside the requested range");
                JsonNode raw = entry.getValue().get(0);
                String status = observationAttribute(attrs.path("observation"), entry.getValue(), "OBS_STATUS");
                BigDecimal value = null;
                // SDMX-JSON represents some missing observations as the string NaN, with status M.
                if (!raw.isNull() && !("NaN".equals(raw.asText()) && "M".equals(status))) {
                    if (!raw.asText().matches("-?[0-9]+(?:\\.[0-9]+)?")) throw invalid("numeric value");
                    value = new BigDecimal(raw.asText()).scaleByPowerOfTen(multiplier);
                }
                if (status.length() > 40) throw invalid("observation status");
                points.add(new PolicyRateData.Point(date, value, status.isEmpty() ? null : status));
            }
            points.sort(Comparator.comparing(PolicyRateData.Point::date));
            result.add(new PolicyRateData.Series(area, "D." + area, title.strip(), compilation + "\n" + breaks
                + "\nBIS daily policy-rate series. Targets, repo/discount rates and target-band midpoints differ by jurisdiction. "
                + "The original BIS instrument history and break notes are retained. https://www.bis.org/statistics/cbpol/cbpol_doc.pdf",
                organization, points));
        }
        return List.copyOf(result);
    }

    private static String observationAttribute(JsonNode definitions, JsonNode values, String id) {
        return attribute(definitions, values, id, 1);
    }
    private static String attribute(JsonNode definitions, JsonNode values, String id) { return attribute(definitions, values, id, 0); }
    private static String attribute(JsonNode definitions, JsonNode values, String id, int offset) {
        for (int i = 0; i < definitions.size(); i++) {
            if (!id.equals(definitions.get(i).path("id").asText())) continue;
            JsonNode index = values.path(i + offset);
            if (index.isNull() || index.isMissingNode()) return "";
            JsonNode options = definitions.get(i).path("values");
            if (!index.isIntegralNumber() || index.intValue() < 0 || index.intValue() >= options.size()) throw invalid("attribute index");
            return options.get(index.intValue()).path("id").asText();
        }
        throw invalid("missing attribute " + id);
    }
    private static IllegalStateException invalid(String field) { return new IllegalStateException("Invalid BIS policy-rate " + field); }
}
