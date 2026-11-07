package com.atlasglobal.ingestion;

import com.atlasglobal.integration.worldbank.WorldBankCatalog;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-native upserts keep ingestion idempotent and commit one provider page at a time. */
@Repository
public class ImportStore {
    private final JdbcTemplate jdbc;
    public ImportStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID ensureSource() {
        jdbc.update("""
            INSERT INTO data_source (id, code, name, base_url, description, documentation_url)
            VALUES (?, 'WORLD_BANK', 'World Bank', 'https://api.worldbank.org/v2/',
                'World Development Indicators',
                'https://datahelpdesk.worldbank.org/knowledgebase/articles/889392')
            ON CONFLICT (code) DO NOTHING
            """, UUID.randomUUID());
        return jdbc.queryForObject("SELECT id FROM data_source WHERE code = 'WORLD_BANK'", UUID.class);
    }

    public UUID start(UUID source, String scope) {
        // Called only while holding the provider advisory lock; older RUNNING rows are abandoned executions.
        jdbc.update("""
            UPDATE data_import SET status = 'FAILED', completed_at = ?, error_message = 'Execution interrupted'
            WHERE data_source_id = ? AND status = 'RUNNING'
            """, Timestamp.from(Instant.now()), source);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO data_import (id, data_source_id, started_at, status, scope)
            VALUES (?, ?, ?, 'RUNNING', ?)
            """, id, source, Timestamp.from(Instant.now()), scope);
        return id;
    }

    @Transactional
    public void countries(List<ProviderData.Country> countries) {
        jdbc.batchUpdate("""
            INSERT INTO country (id, iso2_code, iso3_code, name, original_name, region, income_level, capital, latitude, longitude)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (iso3_code) DO UPDATE SET iso2_code = EXCLUDED.iso2_code, name = EXCLUDED.name,
                original_name = EXCLUDED.original_name, region = EXCLUDED.region,
                income_level = EXCLUDED.income_level, capital = EXCLUDED.capital,
                latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude
            """, countries, 500, (ps, c) -> {
                ps.setObject(1, UUID.randomUUID()); ps.setString(2, c.iso2()); ps.setString(3, c.iso3());
                ps.setString(4, c.name()); ps.setString(5, c.originalName()); ps.setString(6, c.region());
                ps.setString(7, c.incomeLevel()); ps.setString(8, c.capital());
                ps.setBigDecimal(9, c.latitude()); ps.setBigDecimal(10, c.longitude());
            });
    }

    @Transactional
    public UUID series(UUID source, WorldBankCatalog.Entry entry, ProviderData.Indicator metadata) {
        jdbc.update("""
            INSERT INTO indicator (id, code, name, description, category) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
                category = EXCLUDED.category
            """, UUID.randomUUID(), entry.code(), entry.name(), metadata.methodology(), entry.category());
        UUID indicator = jdbc.queryForObject("SELECT id FROM indicator WHERE code = ?", UUID.class, entry.code());
        jdbc.update("""
            INSERT INTO indicator_series (id, indicator_id, data_source_id, external_code, dataset_code,
                original_name, methodology, source_organization, unit, frequency)
            VALUES (?, ?, ?, ?, '2', ?, ?, ?, ?, 'ANNUAL')
            ON CONFLICT (data_source_id, dataset_code, external_code) DO UPDATE SET
                original_name = EXCLUDED.original_name, methodology = EXCLUDED.methodology,
                source_organization = EXCLUDED.source_organization
            """, UUID.randomUUID(), indicator, source, entry.code(), metadata.originalName(),
            metadata.methodology(), metadata.sourceOrganization(), entry.unit());
        return jdbc.queryForObject("""
            SELECT id FROM indicator_series WHERE data_source_id = ? AND dataset_code = '2' AND external_code = ?
            """, UUID.class, source, entry.code());
    }

    @Transactional
    public void observations(UUID importId, UUID series, ProviderData.Batch batch) {
        Instant imported = Instant.now();
        jdbc.batchUpdate("""
            INSERT INTO indicator_observation (id, country_id, series_id, period, value, status,
                provider_status, source_updated_at, imported_at)
            VALUES (?, (SELECT id FROM country WHERE iso3_code = ?), ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (country_id, series_id, period) DO UPDATE SET value = EXCLUDED.value,
                status = EXCLUDED.status, provider_status = EXCLUDED.provider_status,
                source_updated_at = EXCLUDED.source_updated_at, imported_at = EXCLUDED.imported_at
            """, batch.observations(), 500, (ps, o) -> {
                ps.setObject(1, UUID.randomUUID()); ps.setString(2, o.countryCode()); ps.setObject(3, series);
                ps.setInt(4, o.year()); ps.setBigDecimal(5, o.value());
                ps.setString(6, o.value() == null ? "MISSING" : "AVAILABLE");
                ps.setString(7, o.providerStatus());
                ps.setDate(8, batch.sourceUpdatedAt() == null ? null : Date.valueOf(batch.sourceUpdatedAt()));
                ps.setTimestamp(9, Timestamp.from(imported));
            });
        jdbc.update("UPDATE data_import SET records_imported = records_imported + ? WHERE id = ?",
            batch.observations().size(), importId);
    }

    public void complete(UUID id) {
        jdbc.update("UPDATE data_import SET status = 'COMPLETED', completed_at = ? WHERE id = ?",
            Timestamp.from(Instant.now()), id);
    }

    public void fail(UUID id) {
        jdbc.update("UPDATE data_import SET status = 'FAILED', completed_at = ?, error_message = ? WHERE id = ?",
            Timestamp.from(Instant.now()), "Import failed; inspect the operational logs", id);
    }
}
