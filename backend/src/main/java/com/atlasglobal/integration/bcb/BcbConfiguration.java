package com.atlasglobal.integration.bcb;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class BcbConfiguration {
    static final String BASE_URL = "https://api.bcb.gov.br";

    @Bean
    BcbClient bcbClient(ObjectMapper mapper) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(35));
        var http = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory)
            .defaultHeader("User-Agent", "AtlasGlobal/0.1 (https://github.com/lngabriel/atlasglobal)")
            .defaultHeader("Accept", "application/json").build();
        return new BcbClient(http, mapper, 3, Duration.ofSeconds(2), Clock.systemUTC());
    }
}
