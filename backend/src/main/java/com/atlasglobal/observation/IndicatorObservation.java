package com.atlasglobal.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "indicator_observation")
public class IndicatorObservation {
    @Id
    private UUID id;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "series_id")
    private UUID seriesId;

    @Column(name = "period")
    private Integer period;

    @Column(name = "value")
    private BigDecimal value;

    @Column(name = "status")
    private String status;

    @Column(name = "provider_status")
    private String providerStatus;

    @Column(name = "source_updated_at")
    private LocalDate sourceUpdatedAt;

    @Column(name = "imported_at")
    private Instant importedAt;

    protected IndicatorObservation() {
    }

    public UUID getId() { return id; }
    public UUID getCountryId() { return countryId; }
    public UUID getSeriesId() { return seriesId; }
    public Integer getPeriod() { return period; }
    public BigDecimal getValue() { return value; }
    public String getStatus() { return status; }
    public String getProviderStatus() { return providerStatus; }
    public LocalDate getSourceUpdatedAt() { return sourceUpdatedAt; }
    public Instant getImportedAt() { return importedAt; }
}
