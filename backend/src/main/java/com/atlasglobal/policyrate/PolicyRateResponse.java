package com.atlasglobal.policyrate;

import com.atlasglobal.observation.CountryReference;
import com.atlasglobal.source.SourceResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PolicyRateResponse {
    private PolicyRateResponse() {}
    public record Definition(UUID id, String externalCode, String frequency, String instrument, String methodology,
                             String sourceOrganization, String scopeNote, SourceResponse source) {}
    public record Observation(String countryCode, LocalDate date, BigDecimal value, String status,
                              String providerStatus, LocalDate sourceUpdatedAt, Instant importedAt) {}
    public record CountryRate(CountryReference country, Definition series, Observation latestAvailable,
                              String unavailableReason) {}
    public record Result(String code, String name, String unit, LocalDate asOf, List<CountryRate> countries,
                         LocalDate from, LocalDate to, List<Observation> observations) {
        public Result { countries = List.copyOf(countries); observations = List.copyOf(observations); }
    }
}
