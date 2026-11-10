package com.atlasglobal.observation;

import com.atlasglobal.indicator.IndicatorResponse;
import com.atlasglobal.indicator.SeriesQueryRepository;
import com.atlasglobal.shared.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ObservationQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ObservationQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CountryReference> countries(List<String> codes) {
        return jdbc.query("SELECT iso3_code, name FROM country WHERE iso3_code IN (:codes) ORDER BY iso3_code",
            Map.of("codes", codes), (rs, row) -> new CountryReference(rs.getString("iso3_code"), rs.getString("name")));
    }

    public Page<ObservationResponse> history(UUID series, List<String> countries, int from, int to, Pageable page) {
        var parameters = new MapSqlParameterSource().addValue("series", series).addValue("countries", countries)
            .addValue("from", from).addValue("to", to).addValue("limit", page.getPageSize()).addValue("offset", page.getOffset());
        String where = """
            FROM indicator_observation o JOIN country c ON c.id = o.country_id
            WHERE o.series_id = :series AND c.iso3_code IN (:countries) AND o.period BETWEEN :from AND :to
            """;
        Long count = jdbc.queryForObject("SELECT count(*) " + where, parameters, Long.class);
        // Only period may be selected by the caller; country code is a stable tie breaker.
        String order = page.getSort().getOrderFor("period").isDescending() ? "DESC" : "ASC";
        var rows = jdbc.query("SELECT c.iso3_code AS country_code, o.* " + where
            + " ORDER BY o.period " + order + ", c.iso3_code ASC LIMIT :limit OFFSET :offset", parameters,
            (rs, row) -> observation(rs, ""));
        return new PageImpl<>(rows, page, count == null ? 0 : count);
    }

    public Page<CountryIndicatorResponse> latest(String country, String category, String source, Pageable page) {
        var parameters = new MapSqlParameterSource().addValue("country", country).addValue("category", category, java.sql.Types.VARCHAR)
            .addValue("source", source, java.sql.Types.VARCHAR).addValue("limit", page.getPageSize()).addValue("offset", page.getOffset());
        String from = """
            FROM indicator_series s JOIN indicator i ON i.id = s.indicator_id
            JOIN data_source d ON d.id = s.data_source_id
            """;
        String filter = """
            WHERE (:category IS NULL OR i.category = :category)
              AND (:source IS NULL OR LOWER(d.code) = :source)
              AND (d.code NOT IN ('IBGE', 'BCB') OR :country = 'BRA')
            """;
        Long count = jdbc.queryForObject("SELECT count(*) " + from + filter, parameters, Long.class);
        String column = switch (page.getSort().iterator().next().getProperty()) {
            case "name" -> "i.name";
            case "code" -> "i.code";
            case "category" -> "i.category";
            default -> throw ApiException.badRequest("Campo de ordenação inválido.");
        };
        String direction = page.getSort().iterator().next().isDescending() ? "DESC" : "ASC";
        String sql = """
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
                r.imported_at AS reported_imported_at, :country AS country_code
            """ + from + """
            LEFT JOIN LATERAL (
                SELECT o.* FROM indicator_observation o JOIN country c ON c.id = o.country_id
                WHERE o.series_id = s.id AND c.iso3_code = :country AND o.status = 'AVAILABLE'
                ORDER BY o.period DESC LIMIT 1
            ) a ON true
            LEFT JOIN LATERAL (
                SELECT o.* FROM indicator_observation o JOIN country c ON c.id = o.country_id
                WHERE o.series_id = s.id AND c.iso3_code = :country
                ORDER BY o.period DESC LIMIT 1
            ) r ON true
            """ + filter + " ORDER BY " + column + " " + direction + ", i.code, d.code, s.id LIMIT :limit OFFSET :offset";
        var rows = jdbc.query(sql, parameters, (rs, row) -> new CountryIndicatorResponse(
            new IndicatorResponse(rs.getObject("indicator_id", UUID.class), rs.getString("indicator_code"),
                rs.getString("indicator_name"), rs.getString("indicator_description"), rs.getString("category")),
            SeriesQueryRepository.map(rs), nullableObservation(rs, "available_"), nullableObservation(rs, "reported_")));
        return new PageImpl<>(rows, page, count == null ? 0 : count);
    }

    private ObservationResponse nullableObservation(ResultSet rs, String prefix) throws SQLException {
        return rs.getObject(prefix + "period") == null ? null : observation(rs, prefix);
    }

    private ObservationResponse observation(ResultSet rs, String prefix) throws SQLException {
        var imported = rs.getTimestamp(prefix + "imported_at");
        return new ObservationResponse(rs.getString("country_code"), rs.getInt(prefix + "period"),
            rs.getBigDecimal(prefix + "value"), rs.getString(prefix + "status"), rs.getString(prefix + "provider_status"),
            rs.getObject(prefix + "source_updated_at", java.time.LocalDate.class), imported == null ? null : imported.toInstant());
    }
}
