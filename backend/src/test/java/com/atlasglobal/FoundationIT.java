package com.atlasglobal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
class FoundationIT {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestRestTemplate http;
    @LocalServerPort private int applicationPort;
    @Value("${local.management.port}") private int managementPort;

    @Test
    void connectsToPostgresAndAppliesFoundationMigration() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isNotBlank();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class))
            .isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class))
            .containsExactly("flyway_schema_history");
    }

    @Test
    void restrictsActuatorToSeparatePortAndHealthOnly() {
        assertThat(http.getForEntity(url(managementPort, "/actuator/health/readiness"), String.class)
            .getBody()).contains("\"status\":\"UP\"").doesNotContain("components");
        assertThat(http.getForEntity(url(managementPort, "/actuator/env"), String.class)
            .getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity(url(applicationPort, "/actuator/health"), String.class)
            .getStatusCode().value()).isEqualTo(404);
    }

    private String url(int port, String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
