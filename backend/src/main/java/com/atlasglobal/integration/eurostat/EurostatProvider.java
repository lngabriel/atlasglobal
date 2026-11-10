package com.atlasglobal.integration.eurostat;

import com.atlasglobal.ingestion.AnnualDataProvider;
import com.atlasglobal.ingestion.ImportProperties;
import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.integration.RateHttp;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EurostatProvider implements AnnualDataProvider {
    private final EurostatClient client;
    private final JdbcTemplate jdbc;

    public EurostatProvider(ObjectMapper mapper, JdbcTemplate jdbc) {
        this.client = new EurostatClient(RateHttp.create(mapper, "application/json")); this.jdbc = jdbc;
    }

    public ProviderData.Source source() { return EurostatCatalog.SOURCE; }
    public long lockKey() { return 728301952155L; }

    public List<ProviderData.ImportedSeries> load(ImportProperties settings) {
        var countries = new HashMap<String, String>();
        jdbc.query("SELECT iso3_code,iso2_code FROM country", rs -> {
            String code = rs.getString("iso3_code");
            if (settings.countries().contains(code)) countries.put(referenceArea(rs.getString("iso2_code")), code);
        });
        return EurostatCatalog.ENTRIES.stream().map(entry -> new ProviderData.ImportedSeries(entry.definition(), entry.metadata(),
            client.load(entry, countries, settings.fromYear(), settings.endYear()))).toList();
    }

    static String referenceArea(String iso2) {
        return switch (iso2) { case "GR" -> "EL"; case "GB" -> "UK"; default -> iso2; };
    }
}
