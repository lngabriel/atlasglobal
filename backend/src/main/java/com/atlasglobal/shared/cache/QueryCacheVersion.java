package com.atlasglobal.shared.cache;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Import audits invalidate process-local catalog caches even when imports run in another JVM. */
@Component
public class QueryCacheVersion {
    private final JdbcTemplate jdbc;

    public QueryCacheVersion(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String current() {
        return jdbc.queryForObject("""
            SELECT COALESCE(MAX(started_at)::text,'initial') || ':' ||
                COALESCE(MAX(completed_at)::text,'pending') || ':' || COUNT(*)::text FROM data_import
            """, String.class);
    }
}
