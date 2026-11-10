package com.atlasglobal.overview;

import com.atlasglobal.indicator.IndicatorResponse;
import com.atlasglobal.indicator.SeriesQueryRepository;
import com.atlasglobal.integration.worldbank.WorldBankCatalog;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OverviewQueryService {
    private final NamedParameterJdbcTemplate jdbc;

    public OverviewQueryService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Cacheable(cacheNames = "overview", key = "@queryCacheVersion.current()", sync = true)
    public OverviewResponse get() {
        var indicators = jdbc.query("""
            SELECT s.id, s.external_code, s.dataset_code, s.original_name, s.methodology,
                s.source_organization, s.unit, s.frequency, d.id AS source_id,
                d.code AS source_code, d.name AS source_name, d.base_url,
                d.description AS source_description, d.documentation_url,
                i.id AS indicator_id, i.code AS indicator_code, i.name AS indicator_name,
                i.description AS indicator_description, i.category,
                a.period AS available_period, a.value AS available_value, a.status AS available_status,
                a.provider_status AS available_provider_status, a.source_updated_at AS available_source_updated_at,
                a.imported_at AS available_imported_at,
                r.period AS reported_period, r.value AS reported_value, r.status AS reported_status,
                r.provider_status AS reported_provider_status, r.source_updated_at AS reported_source_updated_at,
                r.imported_at AS reported_imported_at
            FROM indicator_series s JOIN indicator i ON i.id = s.indicator_id
            JOIN data_source d ON d.id = s.data_source_id
            LEFT JOIN LATERAL (
                SELECT o.* FROM world_indicator_observation o
                WHERE o.series_id = s.id AND o.status = 'AVAILABLE'
                ORDER BY o.period DESC LIMIT 1
            ) a ON true
            LEFT JOIN LATERAL (
                SELECT o.* FROM world_indicator_observation o
                WHERE o.series_id = s.id ORDER BY o.period DESC LIMIT 1
            ) r ON true
            WHERE i.code IN (:codes) AND d.code = 'WORLD_BANK' AND s.dataset_code = '2'
            ORDER BY i.code
            """, Map.of("codes", WorldBankCatalog.WORLD_INDICATORS), (result, row) -> new OverviewIndicatorResponse(
                new IndicatorResponse(result.getObject("indicator_id", UUID.class), result.getString("indicator_code"),
                    result.getString("indicator_name"), result.getString("indicator_description"), result.getString("category")),
                SeriesQueryRepository.map(result), observation(result, "available_"), observation(result, "reported_")));
        return new OverviewResponse(new OverviewResponse.Scope("WLD", "Mundo"), indicators);
    }

    private WorldObservationResponse observation(ResultSet result, String prefix) throws SQLException {
        if (result.getObject(prefix + "period") == null) return null;
        var imported = result.getTimestamp(prefix + "imported_at");
        return new WorldObservationResponse(result.getInt(prefix + "period"), result.getBigDecimal(prefix + "value"),
            result.getString(prefix + "status"), result.getString(prefix + "provider_status"),
            result.getObject(prefix + "source_updated_at", LocalDate.class), imported == null ? null : imported.toInstant());
    }
}
