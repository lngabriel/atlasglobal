package com.atlasglobal.integration.worldbank;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(WorldBankProperties.class)
class WorldBankConfiguration {
    static final String BASE_URL = "https://api.worldbank.org/v2";

    @Bean
    WorldBankClient worldBankClient(ObjectMapper mapper, WorldBankProperties properties) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout()).build());
        factory.setReadTimeout(properties.readTimeout());
        var rest = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory)
            .defaultHeader("User-Agent", "AtlasGlobal/0.1 (https://github.com/lngabriel/atlasglobal)")
            .build();
        return new WorldBankClient(rest, mapper, properties);
    }
}
