package com.atlasglobal.integration.eurostat;

import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.integration.RateHttp;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Decodes explicit JSON-stat cube positions without constructing observations for absent cells. */
final class EurostatClient {
    private final RateHttp http;
    EurostatClient(RateHttp http) { this.http = http; }

    ProviderData.Batch load(EurostatCatalog.Entry entry, Map<String, String> countries, int from, int to) {
        StringBuilder url = new StringBuilder(EurostatCatalog.SOURCE.baseUrl()).append(entry.dataset())
            .append("?lang=EN&geoLevel=country&sinceTimePeriod=").append(from).append("&untilTimePeriod=").append(to);
        entry.dimensions().entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(dimension -> url.append('&').append(dimension.getKey()).append('=').append(dimension.getValue()));
        return decode(http.get(URI.create(url.toString())), entry, countries, from, to);
    }

    static ProviderData.Batch decode(JsonNode root, EurostatCatalog.Entry entry, Map<String, String> countries, int from, int to) {
        if (!"2.0".equals(root.path("version").asText()) || !"dataset".equals(root.path("class").asText())
            || !"ESTAT".equals(root.path("source").asText()) || !entry.definition().datasetCode().equals(root.path("extension").path("id").asText())
            || !"EN".equals(root.path("extension").path("lang").asText()) || root.path("label").asText().isBlank()) throw invalid("dataset identity");
        JsonNode ids = root.path("id"), sizes = root.path("size");
        if (!ids.isArray() || !sizes.isArray() || ids.size() != entry.dimensions().size() + 2 || ids.size() != sizes.size()) throw invalid("dimensions");
        var expected = new HashSet<>(entry.dimensions().keySet()); expected.add("geo"); expected.add("time");
        var axes = new HashMap<String, List<String>>();
        var strides = new HashMap<String, Integer>();
        int count = 1;
        for (int dimension = ids.size() - 1; dimension >= 0; dimension--) {
            String id = ids.get(dimension).asText();
            if (!expected.remove(id) || !sizes.get(dimension).isIntegralNumber() || !sizes.get(dimension).canConvertToInt()) throw invalid("dimension identity");
            int size = sizes.get(dimension).intValue();
            if (size < 0 || (size == 0 && !id.equals("time")) || size > 1000
                || (size > 0 && count > 100_000 / size)) throw invalid("cube size");
            List<String> positions = positions(root.path("dimension").path(id).path("category").path("index"), size);
            if (entry.dimensions().containsKey(id) && (size != 1 || !positions.getFirst().equals(entry.dimensions().get(id)))) throw invalid("selected " + id);
            if (id.equals("geo") && positions.stream().anyMatch(code -> !code.matches("[A-Z]{2}"))) throw invalid("country geography");
            if (id.equals("time") && positions.stream().anyMatch(year -> !year.matches("[0-9]{4}") || Integer.parseInt(year) < from || Integer.parseInt(year) > to)) throw invalid("annual period");
            axes.put(id, positions); strides.put(id, count); count *= size;
        }
        JsonNode values = root.path("value"), statuses = root.path("status");
        Map<Integer, JsonNode> cells = cells(values, count, false), flags = cells(statuses, count, true);
        var occupied = new TreeSet<>(cells.keySet()); occupied.addAll(flags.keySet());
        var observations = new ArrayList<ProviderData.Observation>();
        for (int index : occupied) {
            String geo = axes.get("geo").get(index / strides.get("geo") % axes.get("geo").size());
            String year = axes.get("time").get(index / strides.get("time") % axes.get("time").size());
            JsonNode raw = cells.get(index), flag = flags.get(index);
            if (raw != null && !raw.isNull() && !raw.isNumber()) throw invalid("numeric value");
            if (flag != null && !flag.isNull() && (!flag.isTextual() || flag.asText().isBlank() || flag.asText().length() > 40)) throw invalid("observation status");
            BigDecimal value = raw == null || raw.isNull() ? null : raw.decimalValue();
            String status = flag == null || flag.isNull() ? null : flag.asText();
            String country = countries.get(geo);
            if (country != null) observations.add(new ProviderData.Observation(country, entry.definition().code(), Integer.parseInt(year), value, status));
        }
        // The JSON-stat updated field and UPDATE_DATA annotation describe the dataset, not per-observation revisions.
        return new ProviderData.Batch(observations, null);
    }

    private static List<String> positions(JsonNode index, int size) {
        if (!index.isObject() || index.size() != size) throw invalid("category index");
        String[] result = new String[size];
        for (var fields = index.fields(); fields.hasNext();) {
            var item = fields.next();
            if (!item.getValue().isIntegralNumber() || !item.getValue().canConvertToInt()) throw invalid("category position");
            int position = item.getValue().intValue();
            if (position < 0 || position >= size || result[position] != null) throw invalid("duplicate category position");
            result[position] = item.getKey();
        }
        return List.of(result);
    }

    private static Map<Integer, JsonNode> cells(JsonNode node, int count, boolean optional) {
        if (optional && (node.isMissingNode() || node.isNull())) return Map.of();
        var cells = new HashMap<Integer, JsonNode>();
        if (node.isArray()) {
            if (node.size() != count) throw invalid("dense cube size");
            for (int index = 0; index < count; index++) {
                if (!optional || !node.get(index).isNull()) cells.put(index, node.get(index));
            }
        } else if (node.isObject()) {
            for (var fields = node.fields(); fields.hasNext();) {
                var item = fields.next();
                if (!item.getKey().matches("0|[1-9][0-9]{0,5}")) throw invalid("cell index");
                int index = Integer.parseInt(item.getKey());
                if (index >= count) throw invalid("cell outside cube");
                if (!optional || !item.getValue().isNull()) cells.put(index, item.getValue());
            }
        } else throw invalid("cell collection");
        return cells;
    }

    private static IllegalStateException invalid(String field) { return new IllegalStateException("Invalid Eurostat " + field); }
}
