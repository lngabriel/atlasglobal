package com.atlasglobal.policyrate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import java.util.List;

@ConfigurationProperties("atlas.policy-rates")
public record PolicyRateProperties(@DefaultValue("2010-01-01") LocalDate from, LocalDate to,
                                   @DefaultValue({"BCB", "BIS", "NBU"}) List<String> providers) {
    public PolicyRateProperties(LocalDate from, LocalDate to) { this(from, to, List.of("BCB", "BIS", "NBU")); }
    @ConstructorBinding
    public PolicyRateProperties {
        providers = List.copyOf(providers);
        if (providers.isEmpty() || providers.stream().distinct().count() != providers.size()
            || providers.stream().anyMatch(code -> !List.of("BCB", "BIS", "NBU").contains(code))) {
            throw new IllegalArgumentException("Invalid policy-rate providers");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate end = to == null ? today : to;
        if (from == null || from.isBefore(LocalDate.of(1900, 1, 1)) || from.isAfter(end) || end.isAfter(today)) {
            throw new IllegalArgumentException("Invalid dated policy-rate import interval");
        }
    }
    public LocalDate end() { return to == null ? LocalDate.now(ZoneOffset.UTC) : to; }
}
