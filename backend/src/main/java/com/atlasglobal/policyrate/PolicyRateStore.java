package com.atlasglobal.policyrate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PolicyRateStore {
    private final JdbcTemplate jdbc;
    public PolicyRateStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Metadata, observations and progress commit together for one country's source definition. */
    @Transactional
    public void persist(UUID execution, UUID source, String country, PolicyRateData.Series data, String scopeNote) {
        int updated = jdbc.update("""
            INSERT INTO policy_rate_series (id,country_id,data_source_id,external_code,frequency,instrument,
                methodology,source_organization,scope_note)
            VALUES (?,(SELECT id FROM country WHERE iso3_code=?),?,?,'DAILY',?,?,?,?)
            ON CONFLICT (country_id,data_source_id,external_code) DO UPDATE SET instrument=EXCLUDED.instrument,
                methodology=EXCLUDED.methodology,source_organization=EXCLUDED.source_organization,scope_note=EXCLUDED.scope_note
            WHERE policy_rate_series.frequency='DAILY'
            """, UUID.randomUUID(), country, source, data.externalCode(), data.instrument(), data.methodology(),
            data.sourceOrganization(), scopeNote);
        if (updated != 1) throw new DataIntegrityViolationException("Incompatible policy-rate frequency");
        UUID series = jdbc.queryForObject("""
            SELECT s.id FROM policy_rate_series s JOIN country c ON c.id=s.country_id
            WHERE c.iso3_code=? AND s.data_source_id=? AND s.external_code=?
            """, UUID.class, country, source, data.externalCode());
        Instant collected = Instant.now();
        jdbc.batchUpdate("""
            INSERT INTO policy_rate_observation (id,series_id,observation_date,value,status,provider_status,imported_at)
            VALUES (?,?,?,?,?,?,?) ON CONFLICT (series_id,observation_date) DO UPDATE SET value=EXCLUDED.value,
                status=EXCLUDED.status,provider_status=EXCLUDED.provider_status,imported_at=EXCLUDED.imported_at
            """, data.points(), 500, (ps, point) -> {
                ps.setObject(1, UUID.randomUUID()); ps.setObject(2, series); ps.setDate(3, Date.valueOf(point.date()));
                ps.setBigDecimal(4, point.value()); ps.setString(5, point.value() == null ? "MISSING" : "AVAILABLE");
                ps.setString(6, point.providerStatus()); ps.setTimestamp(7, Timestamp.from(collected));
            });
        jdbc.update("UPDATE data_import SET records_imported=records_imported+? WHERE id=?", data.points().size(), execution);
    }
}
