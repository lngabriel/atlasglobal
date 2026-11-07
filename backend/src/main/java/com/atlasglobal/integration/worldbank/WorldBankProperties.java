package com.atlasglobal.integration.worldbank;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("atlas.worldbank")
public record WorldBankProperties(@DefaultValue("500") int pageSize,
                                  @DefaultValue("10s") Duration connectTimeout,
                                  @DefaultValue("45s") Duration readTimeout,
                                  @DefaultValue("3") int attempts,
                                  @DefaultValue("1s") Duration retryDelay) {
    public WorldBankProperties {
        if (pageSize < 1 || pageSize > 1000 || attempts < 1 || attempts > 5
            || connectTimeout.isNegative() || connectTimeout.isZero()
            || readTimeout.isNegative() || readTimeout.isZero()
            || retryDelay.isNegative() || retryDelay.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Invalid World Bank client settings");
        }
    }
}
