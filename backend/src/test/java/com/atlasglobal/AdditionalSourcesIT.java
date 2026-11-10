package com.atlasglobal;

import com.atlasglobal.ingestion.AnnualDataProvider;
import com.atlasglobal.ingestion.AnnualImportService;
import com.atlasglobal.ingestion.ImportProperties;
import com.atlasglobal.ingestion.ImportStore;
import com.atlasglobal.ingestion.ProviderData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"atlas.import.schedule-enabled=false", "atlas.additional-import.schedule-enabled=false", "atlas.policy-rates.schedule-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class AdditionalSourcesIT {
    @Autowired ImportStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource database;
    @Autowired MockMvc mvc;
    private final ImportProperties settings = new ImportProperties(List.of("ZZZ", "ZZY"), 2020, 2026);
    private final AtomicReference<List<ProviderData.ImportedSeries>> data = new AtomicReference<>();
    private ImportProperties requested;
    private int calls;

    @BeforeEach
    void prepare() {
        store.countries(List.of(country("ZZ", "ZZZ"), country("ZY", "ZZY")));
        data.set(List.of(imported(List.of(point("ZZZ", 2024, "12.1234567890123456789", "IMF_HISTORICAL"),
            point("ZZZ", 2026, "0", "IMF_PROJECTION"), point("ZZY", 2026, "-2.4", "IMF_PROJECTION"),
            point("ZZY", 2024, null, "IMF_HISTORICAL")))));
    }

    @Test
    void persistsOfficialClassificationsAndRevisesIdempotentlyWithoutDeletingHistory() {
        var importer = importer(provider());
        UUID first = importer.run("IMF");
        data.set(List.of(imported(List.of(point("ZZZ", 2026, "0.125", "IMF_PROJECTION")))));
        importer.run("IMF");
        UUID last = importer.run("IMF");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM indicator_observation WHERE series_id=?", Long.class, series())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT value FROM indicator_observation WHERE series_id=? AND period=2024 AND status='AVAILABLE'", BigDecimal.class, series()))
            .isEqualByComparingTo("12.1234567890123456789");
        assertThat(jdbc.queryForObject("SELECT provider_status FROM indicator_observation WHERE series_id=? AND period=2026 AND country_id=(SELECT id FROM country WHERE iso3_code='ZZZ')", String.class, series()))
            .isEqualTo("IMF_PROJECTION");
        assertThat(jdbc.queryForObject("SELECT records_imported FROM data_import WHERE id=?", Long.class, first)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT status FROM data_import WHERE id=?", String.class, last)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM indicator_observation WHERE series_id=? AND source_updated_at IS NOT NULL", Long.class, series())).isZero();
    }

    @Test
    void exposesProjectionFlagsInHistoryRankingsAndCompleteCsvWithoutProviderCalls() throws Exception {
        importer(provider()).run("IMF");
        mvc.perform(get("/api/v1/countries/ZZZ/indicators/IMF.TEST.DEBT").param("from", "2026").param("to", "2026"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.observations.content[0].providerStatus").value("IMF_PROJECTION"));
        mvc.perform(get("/api/v1/indicators/IMF.TEST.DEBT/ranking").param("year", "2026").param("direction", "desc"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ranking.content[0].value").value(0))
            .andExpect(jsonPath("$.ranking.content[0].providerStatus").value("IMF_PROJECTION"));
        String csv = new String(mvc.perform(get("/api/v1/exports/indicators").param("indicator", "IMF.TEST.DEBT")
                .param("countries", "ZZZ,ZZY").param("from", "2024").param("to", "2026").accept("text/csv"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("IMF_PROJECTION", "12.1234567890123456789", "MISSING");
        assertThat(calls).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateOrOutOfScopeBatchesBeforeTheyArePersisted() {
        var point = point("ZZZ", 2024, "1", null);
        for (var points : List.of(List.of(point, point), List.of(point("USA", 2024, "1", null)),
                List.of(point("ZZZ", 2019, "1", null)))) {
            data.set(List.of(imported(points)));
            assertThatThrownBy(() -> importer(provider()).run("IMF")).hasMessageContaining("out-of-scope");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM indicator WHERE code='IMF.TEST.DEBT'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM data_import WHERE status='FAILED' AND data_source_id=(SELECT id FROM data_source WHERE code='IMF')", Long.class)).isEqualTo(3);
    }

    @Test
    void requiresTheCountryCatalogBeforeAnyUpstreamRequest() {
        var missing = new AnnualImportService(List.of(provider()), store,
            new ImportProperties(List.of("ZZX"), 2020, 2026), jdbc, database);
        assertThatThrownBy(() -> missing.run("IMF")).hasMessageContaining("country catalog");
        assertThat(calls).isZero();
    }

    @Test
    void keepsHistoryAndSanitizesPersistedErrorsAfterAnUpstreamFailure() {
        importer(provider()).run("IMF");
        var failing = new AnnualDataProvider() {
            public ProviderData.Source source() { return provider().source(); }
            public long lockKey() { return 728301952153L; }
            public List<ProviderData.ImportedSeries> load(ImportProperties interval) { throw new IllegalStateException("Test provider failure"); }
        };
        assertThatThrownBy(() -> importer(failing).run("IMF")).hasMessageContaining("Test provider failure");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM indicator_observation WHERE series_id=?", Long.class, series())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT error_message FROM data_import WHERE status='FAILED' AND data_source_id=(SELECT id FROM data_source WHERE code='IMF')", String.class))
            .isEqualTo("Import failed; inspect the operational logs");
    }

    @Test
    void preventsConcurrentImportsAcrossDatabaseSessions() throws Exception {
        try (var connection = database.getConnection(); var lock = connection.createStatement()) {
            lock.execute("SELECT pg_advisory_lock(728301952153)");
            try { assertThatThrownBy(() -> importer(provider()).run("IMF")).hasMessageContaining("already running"); }
            finally { lock.execute("SELECT pg_advisory_unlock(728301952153)"); }
        }
        assertThat(calls).isZero();
    }

    @Test
    void refreshesOnlySixRecentYearsWithinTheConfiguredInterval() {
        data.set(List.of());
        importer(provider()).refreshRecent("IMF");
        assertThat(requested.fromYear()).isEqualTo(2021);
        assertThat(requested.endYear()).isEqualTo(2026);
    }

    private AnnualImportService importer(AnnualDataProvider provider) { return new AnnualImportService(List.of(provider), store, settings, jdbc, database); }
    private AnnualDataProvider provider() {
        return new AnnualDataProvider() {
            public ProviderData.Source source() { return new ProviderData.Source("IMF", "IMF", "https://www.imf.org/external/datamapper/api/v2/", "Test provider", "https://www.imf.org/"); }
            public long lockKey() { return 728301952153L; }
            public List<ProviderData.ImportedSeries> load(ImportProperties interval) { calls++; requested = interval; return data.get(); }
        };
    }
    private UUID series() { return jdbc.queryForObject("SELECT s.id FROM indicator_series s JOIN indicator i ON i.id=s.indicator_id WHERE i.code='IMF.TEST.DEBT'", UUID.class); }
    private ProviderData.Country country(String iso2, String iso3) { return new ProviderData.Country(iso2, iso3, iso3, iso3, null, null, null, null, null); }
    private ProviderData.Observation point(String country, int year, String value, String flag) { return new ProviderData.Observation(country, "IMF.TEST.DEBT", year, value == null ? null : new BigDecimal(value), flag); }
    private ProviderData.ImportedSeries imported(List<ProviderData.Observation> points) {
        return new ProviderData.ImportedSeries(new ProviderData.Series("IMF.TEST.DEBT", "Test debt", "ECONOMY", "PERCENT_GDP", "TEST_WEO:2026-10"),
            new ProviderData.Indicator("TEST_DEBT", "Test debt", "Official historical estimates and projections", "IMF"), new ProviderData.Batch(points, (LocalDate)null));
    }
}
