package com.atlasglobal;

import com.atlasglobal.country.Country;
import com.atlasglobal.ingestion.ImportStore;
import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.integration.worldbank.WorldBankCatalog;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ImportStoreIT {
    @Autowired ImportStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired com.atlasglobal.ingestion.WorldBankImportService importer;
    @Autowired javax.sql.DataSource database;

    @Test
    void importsIdempotentlyRevisesValuesAndPreservesHistory() {
        var country = new ProviderData.Country("ZZ", "ZZZ", "Test country", "Test country", "Test region",
            null, null, null, null);
        store.countries(List.of(country));
        store.countries(List.of(country));
        UUID source = store.ensureSource();
        UUID run = store.start(source, "integration-test");
        var catalog = WorldBankCatalog.ENTRIES.getFirst();
        var metadata = new ProviderData.Indicator(catalog.code(), "Test GDP", "Test definition", "Test provider");
        UUID series = store.series(source, catalog, metadata);
        assertThat(store.series(source, catalog, metadata)).isEqualTo(series);
        store.observations(run, series, batch(catalog.code(), "2023", "12.1234567890123456789"));
        store.observations(run, series, batch(catalog.code(), "2024", "0"));
        store.observations(run, series, batch(catalog.code(), "2024", null));
        store.complete(run);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM country WHERE iso3_code = 'ZZZ'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM indicator_observation WHERE series_id = ?", Integer.class, series)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT value FROM indicator_observation WHERE series_id = ? AND period = 2023",
            BigDecimal.class, series)).isEqualByComparingTo("12.1234567890123456789");
        assertThat(jdbc.queryForObject("SELECT status FROM indicator_observation WHERE series_id = ? AND period = 2024",
            String.class, series)).isEqualTo("MISSING");
        assertThat(jdbc.queryForObject("SELECT records_imported FROM data_import WHERE id = ?", Long.class, run)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM data_import WHERE id = ?", String.class, run)).isEqualTo("COMPLETED");
        var mapped = entities.createQuery("select c from Country c where c.iso3Code = :code", Country.class)
            .setParameter("code", "ZZZ").getSingleResult();
        assertThat(mapped.getOriginalName()).isEqualTo("Test country");
    }

    @Test
    void recordsFailuresAndRecoversAbandonedRuns() {
        UUID source = store.ensureSource();
        UUID abandoned = store.start(source, "abandoned-test");
        UUID failed = store.start(source, "failed-test");
        store.fail(failed);
        assertThat(jdbc.queryForObject("SELECT error_message FROM data_import WHERE id = ?", String.class, abandoned))
            .isEqualTo("Execution interrupted");
        assertThat(jdbc.queryForObject("SELECT status FROM data_import WHERE id = ?", String.class, failed)).isEqualTo("FAILED");
    }

    @Test
    void preventsConcurrentImportsAcrossDatabaseSessions() throws Exception {
        try (var connection = database.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(728301952147)");
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(importer::run)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("already running");
            } finally { statement.execute("SELECT pg_advisory_unlock(728301952147)"); }
        }
    }

    @Test
    void rejectsAUnitChangeForAnExistingSeries() {
        UUID source = store.ensureSource();
        var entry = WorldBankCatalog.ENTRIES.getFirst();
        var metadata = new ProviderData.Indicator(entry.code(), "Test GDP", "Test definition", "Test provider");
        store.series(source, entry, metadata);
        var incompatible = new WorldBankCatalog.Entry(entry.code(), entry.name(), entry.category(), "EUR_CURRENT");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.series(source, incompatible, metadata))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("unit");
    }

    private ProviderData.Batch batch(String indicator, String year, String value) {
        return new ProviderData.Batch(List.of(new ProviderData.Observation("ZZZ", indicator, Integer.parseInt(year),
            value == null ? null : new BigDecimal(value), "")), LocalDate.of(2026, 10, 8));
    }
}
