package com.atlasglobal.ingestion;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persists official world aggregates independently from country observations. */
@Repository
public class OverviewImportStore {
    private final JdbcTemplate jdbc;

    public OverviewImportStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void observations(UUID importId, UUID series, ProviderData.Batch batch) {
        if (batch.observations().stream().anyMatch(value -> !"WLD".equals(value.countryCode()))) {
            throw new IllegalArgumentException("World observations require the WLD scope");
        }
        Instant imported = Instant.now();
        jdbc.batchUpdate("""
            INSERT INTO world_indicator_observation (id, series_id, period, value, status,
                provider_status, source_updated_at, imported_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (series_id, period) DO UPDATE SET value = EXCLUDED.value,
                status = EXCLUDED.status, provider_status = EXCLUDED.provider_status,
                source_updated_at = EXCLUDED.source_updated_at, imported_at = EXCLUDED.imported_at
            """, batch.observations(), 500, (statement, observation) -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, series);
                statement.setInt(3, observation.year());
                statement.setBigDecimal(4, observation.value());
                statement.setString(5, observation.value() == null ? "MISSING" : "AVAILABLE");
                statement.setString(6, observation.providerStatus());
                statement.setDate(7, batch.sourceUpdatedAt() == null ? null : Date.valueOf(batch.sourceUpdatedAt()));
                statement.setTimestamp(8, Timestamp.from(imported));
            });
        jdbc.update("UPDATE data_import SET records_imported = records_imported + ? WHERE id = ?",
            batch.observations().size(), importId);
    }
}
