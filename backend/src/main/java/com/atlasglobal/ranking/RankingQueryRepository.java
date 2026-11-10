package com.atlasglobal.ranking;

import com.atlasglobal.observation.CountryReference;
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
class RankingQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    RankingQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<Integer> availableYears(UUID series) {
        return jdbc.query("""
            SELECT DISTINCT period FROM indicator_observation
            WHERE series_id = :series AND status = 'AVAILABLE' AND value IS NOT NULL
            ORDER BY period DESC
            """, Map.of("series", series), (rs, row) -> rs.getInt("period"));
    }

    long totalCountries() {
        Long total = jdbc.queryForObject("SELECT count(*) FROM country", Map.of(), Long.class);
        return total == null ? 0 : total;
    }

    Page<RankingEntry> ranking(UUID series, Integer year, Pageable page) {
        if (year == null) return new PageImpl<>(List.of(), page, 0);
        var parameters = new MapSqlParameterSource().addValue("series", series).addValue("year", year)
            .addValue("limit", page.getPageSize()).addValue("offset", page.getOffset());
        String from = """
            FROM indicator_observation o JOIN country c ON c.id = o.country_id
            WHERE o.series_id = :series AND o.period = :year
              AND o.status = 'AVAILABLE' AND o.value IS NOT NULL
            """;
        Long count = jdbc.queryForObject("SELECT count(*) " + from, parameters, Long.class);
        // The caller validates value-only sorting. SQL direction comes from an enum, never raw input.
        String direction = page.getSort().getOrderFor("value").isDescending() ? "DESC" : "ASC";
        var rows = jdbc.query("""
            SELECT RANK() OVER (ORDER BY o.value %s) AS position,
                   c.iso3_code, c.name, o.value, o.provider_status
            """.formatted(direction) + from + " ORDER BY o.value " + direction
            + ", c.iso3_code ASC LIMIT :limit OFFSET :offset", parameters,
            (rs, row) -> new RankingEntry(rs.getLong("position"),
                new CountryReference(rs.getString("iso3_code"), rs.getString("name")), rs.getBigDecimal("value"), rs.getString("provider_status")));
        return new PageImpl<>(rows, page, count == null ? 0 : count);
    }
}
