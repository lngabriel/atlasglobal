package com.atlasglobal.shared;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Validates the public deployment boundary before datasource initialization. */
@Configuration(proxyBeanMethods = false)
@Profile("render")
public class RenderConfiguration {
    static final String JDBC_OPTIONS = "?sslmode=verify-full"
        + "&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory"
        + "&connectTimeout=10&socketTimeout=60&tcpKeepAlive=true";

    @Bean
    static BeanFactoryPostProcessor validateRenderEnvironment(Environment environment) {
        return factory -> validate(environment);
    }

    static void validate(Environment environment) {
        String host = required(environment, "DB_HOST");
        if (!host.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.neon\\.tech")
            || host.split("\\.", 2)[0].endsWith("-pooler")) {
            throw new IllegalArgumentException("DB_HOST must be a direct Neon hostname without pooling");
        }
        String database = required(environment, "DB_NAME");
        if (!database.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,62}")) {
            throw new IllegalArgumentException("DB_NAME must be a plain PostgreSQL database name");
        }
        required(environment, "DB_USERNAME");
        required(environment, "DB_PASSWORD");
        String origin = required(environment, "CORS_ALLOWED_ORIGINS");
        validateOrigin(origin);
        if (!origin.equals(environment.getProperty("atlas.cors.allowed-origins"))) {
            throw new IllegalArgumentException("The render profile requires its exact configured CORS origin");
        }

        String expectedUrl = "jdbc:postgresql://" + host + ":5432/" + database + JDBC_OPTIONS;
        if (!expectedUrl.equals(environment.getProperty("spring.datasource.url"))) {
            throw new IllegalArgumentException("The render profile requires its verified TLS datasource URL");
        }
        String flywayUrl = environment.getProperty("spring.flyway.url");
        if (flywayUrl != null && !expectedUrl.equals(flywayUrl)) {
            throw new IllegalArgumentException("Flyway must use the same direct verified TLS connection");
        }
        if (!"127.0.0.1".equals(environment.getProperty("management.server.address"))) {
            throw new IllegalArgumentException("Management endpoints must remain on loopback");
        }
        if (!"8081".equals(environment.getProperty("management.server.port"))
            || "8081".equals(environment.getProperty("server.port"))) {
            throw new IllegalArgumentException("Management endpoints require a separate private port");
        }
    }

    private static String required(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException(name + " must be configured privately for the render profile");
        }
        return value;
    }

    private static void validateOrigin(String value) {
        try {
            URI origin = new URI(value);
            if (!"https".equals(origin.getScheme()) || origin.getHost() == null
                || !origin.getHost().matches("[a-z0-9-]+\\.pages\\.dev")
                || origin.getRawUserInfo() != null || origin.getPort() != -1
                || (origin.getRawPath() != null && !origin.getRawPath().isEmpty())
                || origin.getRawQuery() != null || origin.getRawFragment() != null) {
                throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS requires one exact HTTPS pages.dev origin");
            }
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS requires one exact HTTPS pages.dev origin");
        }
    }
}
