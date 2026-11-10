package com.atlasglobal.shared;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderConfigurationTest {
    @Test
    void acceptsOnlyTheConfiguredDirectVerifiedTlsConnectionAndPublicOrigin() {
        assertThatCode(() -> RenderConfiguration.validate(environment())).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingDeploymentConfigurationBeforeInstantiatingApplicationBeans() {
        AtomicBoolean initialized = new AtomicBoolean();
        new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("render"))
            .withUserConfiguration(RenderConfiguration.class)
            .withBean("probe", Object.class, () -> {
                initialized.set(true);
                return new Object();
            })
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("DB_HOST");
                assertThat(initialized).isFalse();
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_HOST", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "CORS_ALLOWED_ORIGINS"})
    void requiresDeploymentValuesBeforeOpeningConnections(String name) {
        MockEnvironment missing = environment();
        missing.setProperty(name, " ");
        assertThatThrownBy(() -> RenderConfiguration.validate(missing))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ep-test-pooler.us-east-2.aws.neon.tech", "localhost",
        "ep-test.neon.tech?sslmode=disable", "ep-test.neon.tech.evil.example", "ep-test.neon.tech/other"})
    void rejectsPoolingAndHostnameInjection(String host) {
        assertThatThrownBy(() -> RenderConfiguration.validate(environment().withProperty("DB_HOST", host)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DB_HOST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.pages.dev", "http://atlas.pages.dev", "https://atlas.pages.dev/",
        "https://atlas.pages.dev/path", "https://user@atlas.pages.dev", "https://atlas.pages.dev?other=true",
        "https://atlas.pages.dev#fragment", "https://atlas.pages.dev,https://preview.pages.dev",
        "https://atlas.pages.dev.evil.example", "https://atlas.pages.dev:444"})
    void rejectsUntrustedAndNonOriginCorsConfiguration(String origin) {
        assertThatThrownBy(() -> RenderConfiguration.validate(environment().withProperty("CORS_ALLOWED_ORIGINS", origin)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void rejectsOverridesOfTlsFlywayCorsAndManagementBoundary() {
        assertThatThrownBy(() -> RenderConfiguration.validate(environment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://ep-test.neon.tech/db?sslmode=require")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("verified TLS");
        assertThatThrownBy(() -> RenderConfiguration.validate(environment()
            .withProperty("spring.flyway.url", "jdbc:postgresql://localhost/db")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Flyway");
        assertThatThrownBy(() -> RenderConfiguration.validate(environment()
            .withProperty("atlas.cors.allowed-origins", "https://preview.pages.dev")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CORS");
        assertThatThrownBy(() -> RenderConfiguration.validate(environment()
            .withProperty("management.server.address", "0.0.0.0")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("loopback");
        assertThatThrownBy(() -> RenderConfiguration.validate(environment()
            .withProperty("management.server.port", "10000")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("separate private port");
        assertThatThrownBy(() -> RenderConfiguration.validate(environment()
            .withProperty("server.port", "8081")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("separate private port");
    }

    @ParameterizedTest
    @ValueSource(strings = {"db?sslmode=disable", "db/other", "db#fragment", "db&sslmode=disable"})
    void rejectsDatabaseNameInjection(String database) {
        assertThatThrownBy(() -> RenderConfiguration.validate(environment().withProperty("DB_NAME", database)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DB_NAME");
    }

    private MockEnvironment environment() {
        return new MockEnvironment()
            .withProperty("DB_HOST", "ep-test.us-east-2.aws.neon.tech")
            .withProperty("DB_NAME", "atlas_global")
            .withProperty("DB_USERNAME", "atlas_test")
            .withProperty("DB_PASSWORD", "synthetic-test-only")
            .withProperty("CORS_ALLOWED_ORIGINS", "https://atlas-global.pages.dev")
            .withProperty("atlas.cors.allowed-origins", "https://atlas-global.pages.dev")
            .withProperty("management.server.address", "127.0.0.1")
            .withProperty("management.server.port", "8081")
            .withProperty("server.port", "10000")
            .withProperty("spring.datasource.url", "jdbc:postgresql://ep-test.us-east-2.aws.neon.tech:5432/atlas_global"
                + RenderConfiguration.JDBC_OPTIONS);
    }
}
