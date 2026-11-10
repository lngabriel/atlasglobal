package com.atlasglobal.integration.un;

import com.atlasglobal.ingestion.AnnualDataProvider;
import com.atlasglobal.ingestion.ImportProperties;
import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.integration.CsvRecords;
import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class UnProvider implements AnnualDataProvider {
    private final UnClient client;
    UnProvider(UnClient client) { this.client = client; }
    public ProviderData.Source source() { return UnCatalog.SOURCE; }
    public long lockKey() { return 728301952154L; }
    public List<ProviderData.ImportedSeries> load(ImportProperties settings) { return decode(client.load(), settings); }

    static List<ProviderData.ImportedSeries> decode(UnClient.Snapshot snapshot, ImportProperties settings) {
        var rows = CsvRecords.records(snapshot.csv());
        if (rows.isEmpty() || !rows.getFirst().keySet().containsAll(List.of("iso3", "country", "hdicode", "region"))) {
            throw new IllegalStateException("Invalid UNDP country metadata columns");
        }
        var seen = new HashSet<String>();
        for (var row : rows) {
            String code = row.get("iso3");
            if (code.matches("[A-Z]{3}") && (!seen.add(code) || row.get("country").isBlank())) {
                throw new IllegalStateException("Duplicate or invalid UNDP country identity");
            }
        }
        var imported = new ArrayList<ProviderData.ImportedSeries>();
        for (var entry : UnCatalog.ENTRIES) {
            var years = years(rows.getFirst(), entry.prefix());
            var observations = new ArrayList<ProviderData.Observation>();
            for (var row : rows) {
                String code = row.get("iso3");
                // The source includes regional/development aggregates; matching is only against operational ISO3 identities.
                if (!code.matches("[A-Z]{3}") || !settings.countries().contains(code)) continue;
                for (int year : years) {
                    String raw = row.get(entry.prefix() + "_" + year).strip();
                    BigDecimal value = null;
                    if (!raw.isEmpty()) {
                        if (!raw.matches("[0-9]+(?:\\.[0-9]+)?")) throw new IllegalStateException("Invalid UNDP index value");
                        value = new BigDecimal(raw);
                        if (value.compareTo(BigDecimal.ONE) > 0) throw new IllegalStateException("UNDP index is outside its 0–1 scale");
                    }
                    if (year >= settings.fromYear() && year <= settings.endYear()) {
                        observations.add(new ProviderData.Observation(code, entry.code(), year, value, null));
                    }
                }
            }
            imported.add(new ProviderData.ImportedSeries(entry.definition(snapshot.edition()), entry.metadata(snapshot.edition(), years.getLast()),
                new ProviderData.Batch(observations, null)));
        }
        return List.copyOf(imported);
    }

    private static TreeSet<Integer> years(Map<String, String> row, String prefix) {
        var years = new TreeSet<Integer>();
        for (String column : row.keySet()) {
            if (!column.matches(prefix + "_[0-9]{4}")) continue;
            int year = Integer.parseInt(column.substring(prefix.length() + 1));
            if (year < 1990 || year > Year.now(ZoneOffset.UTC).getValue()) throw new IllegalStateException("Invalid UNDP annual period");
            years.add(year);
        }
        if (years.isEmpty()) throw new IllegalStateException("Missing UNDP " + prefix + " annual columns");
        return years;
    }
}
