package com.atlasglobal.shared;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(WebConfiguration.CorsProperties.class)
public class WebConfiguration implements WebMvcConfigurer {
    private final CorsProperties cors;

    public WebConfiguration(CorsProperties cors) {
        this.cors = cors;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
            .allowedMethods("GET", "HEAD", "OPTIONS")
            .allowedHeaders("Accept", "Content-Type")
            .allowCredentials(false)
            .maxAge(3600);
    }

    @ConfigurationProperties("atlas.cors")
    public record CorsProperties(List<String> allowedOrigins) {
        public CorsProperties {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
            if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
                throw new IllegalArgumentException("CORS requires explicit origins");
            }
        }
    }
}
