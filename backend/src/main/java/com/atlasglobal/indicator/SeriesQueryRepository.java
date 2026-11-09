package com.atlasglobal.indicator;

import com.atlasglobal.source.SourceResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SeriesQueryRepository {
    private final JdbcTemplate jdbc;
    public SeriesQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<SeriesResponse> forIndicator(UUID indicator) {
        return jdbc.query("""
            SELECT s.id, s.external_code, s.dataset_code, s.original_name, s.methodology,
                   s.source_organization, s.unit, s.frequency, d.id AS source_id,
                   d.code AS source_code, d.name AS source_name, d.base_url,
                   d.description AS source_description, d.documentation_url
            FROM indicator_series s JOIN data_source d ON d.id = s.data_source_id
            WHERE s.indicator_id = ? ORDER BY d.code, s.dataset_code, s.external_code, s.id
            """, (rs, row) -> map(rs), indicator);
    }

    public static SeriesResponse map(ResultSet rs) throws SQLException {
        return new SeriesResponse(rs.getObject("id", UUID.class), rs.getString("external_code"),
            rs.getString("dataset_code"), rs.getString("original_name"), rs.getString("methodology"),
            rs.getString("source_organization"), rs.getString("unit"), rs.getString("frequency"),
            new SourceResponse(rs.getObject("source_id", UUID.class), rs.getString("source_code"),
                rs.getString("source_name"), rs.getString("base_url"), rs.getString("source_description"),
                rs.getString("documentation_url")));
    }
}
