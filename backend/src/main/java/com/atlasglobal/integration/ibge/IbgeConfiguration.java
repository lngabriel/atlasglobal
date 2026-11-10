package com.atlasglobal.integration.ibge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class IbgeConfiguration {
    static final String BASE_URL = "https://servicodados.ibge.gov.br/api/v3/agregados";

    @Bean
    IbgeClient ibgeClient(ObjectMapper mapper) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(40));
        var http = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory)
            .defaultHeader("User-Agent", "AtlasGlobal/0.1 (https://github.com/lngabriel/atlasglobal)")
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Accept-Encoding", "identity")
            .build();
        return new IbgeClient(http, mapper, 3, Duration.ofSeconds(2));
    }
}
