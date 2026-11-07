package com.atlasglobal.ingestion;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("atlas.import")
public record ImportProperties(
    @DefaultValue({"BRA", "USA", "CHN", "RUS", "DEU", "JPN", "GBR", "FRA", "IND"}) List<String> countries,
    @DefaultValue("1960") int fromYear,
    @DefaultValue("0") int toYear) {
    public ImportProperties {
        countries = List.copyOf(countries);
        if (toYear == 0) toYear = Year.now(ZoneOffset.UTC).getValue();
        if (countries.isEmpty() || countries.size() > 50 || countries.stream().distinct().count() != countries.size()
            || countries.stream().anyMatch(c -> !c.matches("[A-Z0-9]{3}"))
            || fromYear < 1960 || toYear < fromYear || toYear > Year.now(ZoneOffset.UTC).getValue()) {
            throw new IllegalArgumentException("Invalid import countries or year interval");
        }
    }
}
