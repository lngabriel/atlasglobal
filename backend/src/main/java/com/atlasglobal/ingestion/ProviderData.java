package com.atlasglobal.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Provider-independent values used by the importer; no external response types cross this boundary. */
public final class ProviderData {
    private ProviderData() {}

    public record Source(String code, String name, String baseUrl, String description,
                         String documentationUrl) {}
    public record Series(String code, String name, String category, String unit, String datasetCode) {}

    public record Country(String iso2, String iso3, String name, String originalName,
                          String region, String incomeLevel, String capital,
                          BigDecimal latitude, BigDecimal longitude) {}
    public record Indicator(String externalCode, String originalName, String methodology,
                            String sourceOrganization) {}
    public record Observation(String countryCode, String indicatorCode, int year,
                              BigDecimal value, String providerStatus) {}
    public record Batch(List<Observation> observations, LocalDate sourceUpdatedAt) {
        public Batch { observations = List.copyOf(observations); }
    }
    public record ImportedSeries(Series definition, Indicator metadata, Batch batch) {}
}
