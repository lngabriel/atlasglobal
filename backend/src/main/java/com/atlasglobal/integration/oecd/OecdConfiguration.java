package com.atlasglobal.integration.oecd;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class OecdConfiguration {
    static final String BASE_URL = "https://sdmx.oecd.org/public/rest/v1";

    @Bean
    OecdClient oecdClient() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(45));
        var http = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory)
            .defaultHeader("User-Agent", "AtlasGlobal/0.1 (https://github.com/lngabriel/atlasglobal)")
            .defaultHeader("Accept", "text/csv")
            .build();
        return new OecdClient(http, 3, Duration.ofSeconds(2));
    }
}
