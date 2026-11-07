package com.atlasglobal.integration.worldbank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

final class WorldBankDtos {
    private WorldBankDtos() {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Label(String id, String value) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Country(String id, String iso2Code, String name, Label region, Label incomeLevel,
                   String capitalCity, String latitude, String longitude) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Indicator(String id, String name, String unit, Label source,
                     String sourceNote, String sourceOrganization) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Observation(Label indicator, String countryiso3code, String date, BigDecimal value,
                       String unit, String obs_status) {}
}
