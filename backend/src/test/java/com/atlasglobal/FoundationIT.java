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
    @Autowired private org.springframework.context.ApplicationContext context;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
    @LocalServerPort private int applicationPort;
    @Value("${local.management.port}") private int managementPort;

    @Test
    void connectsToPostgresAndAppliesCatalogMigrations() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isNotBlank();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class))
            .isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class))
            .contains("flyway_schema_history", "country", "indicator", "data_source",
                "indicator_series", "indicator_observation", "data_import");
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

    @Test
    void exposesOnlyMinimalUncachedAvailabilityAndProtectsDirectResponses() {
        var response = http.getForEntity(url(applicationPort, "/healthz"), java.util.Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsOnlyKeys("status");
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).contains("frame-ancestors 'none'");
    }

    @Test
    void doesNotExposeTheXsltOrSseFragmentFeaturesFlaggedBySpringAdvisories() {
        assertThat(context.getBeansOfType(org.springframework.web.servlet.view.xslt.XsltView.class)).isEmpty();
        assertThat(context.getBeansOfType(org.springframework.web.servlet.view.xslt.XsltViewResolver.class)).isEmpty();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            assertThat(mapping.getProducesCondition().getProducibleMediaTypes())
                .doesNotContain(org.springframework.http.MediaType.TEXT_EVENT_STREAM);
            assertThat(handler.getMethod().getGenericReturnType().getTypeName())
                .doesNotContain("FragmentsRendering", "SseEmitter");
        });
    }

    private String url(int port, String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
