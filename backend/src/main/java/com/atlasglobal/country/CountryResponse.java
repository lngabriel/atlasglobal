package com.atlasglobal.country;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Country or economy in the catalog, with metadata preserved from the source")
public record CountryResponse(
    UUID id,
    String iso2Code,
    String iso3Code,
    String name,
    String originalName,
    String region,
    String incomeLevel,
    @Schema(description = "Capital reported by the source; may be unavailable") String capital,
    BigDecimal latitude,
    BigDecimal longitude
) {
    public static CountryResponse from(Country country) {
        return new CountryResponse(country.getId(), country.getIso2Code(), country.getIso3Code(),
            country.getName(), country.getOriginalName(), country.getRegion(), country.getIncomeLevel(),
            country.getCapital(), country.getLatitude(), country.getLongitude());
    }
}
