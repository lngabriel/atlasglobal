package com.atlasglobal.country;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "País ou economia do catálogo, com metadados preservados da fonte")
public record CountryResponse(
    UUID id,
    String iso2Code,
    String iso3Code,
    String name,
    String originalName,
    String region,
    String incomeLevel,
    @Schema(description = "Capital informada pela fonte; pode estar ausente") String capital,
    BigDecimal latitude,
    BigDecimal longitude
) {
    public static CountryResponse from(Country country) {
        return new CountryResponse(country.getId(), country.getIso2Code(), country.getIso3Code(),
            country.getName(), country.getOriginalName(), country.getRegion(), country.getIncomeLevel(),
            country.getCapital(), country.getLatitude(), country.getLongitude());
    }
}
