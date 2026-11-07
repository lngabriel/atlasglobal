package com.atlasglobal.indicator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "indicator_series")
public class IndicatorSeries {
    @Id
    private UUID id;

    @Column(name = "indicator_id")
    private UUID indicatorId;

    @Column(name = "data_source_id")
    private UUID dataSourceId;

    @Column(name = "external_code")
    private String externalCode;

    @Column(name = "dataset_code")
    private String datasetCode;

    @Column(name = "original_name", columnDefinition = "text")
    private String originalName;

    @Column(name = "methodology", columnDefinition = "text")
    private String methodology;

    @Column(name = "source_organization", columnDefinition = "text")
    private String sourceOrganization;

    @Column(name = "unit")
    private String unit;

    @Column(name = "frequency")
    private String frequency;

    protected IndicatorSeries() {
    }

    public UUID getId() { return id; }
    public UUID getIndicatorId() { return indicatorId; }
    public UUID getDataSourceId() { return dataSourceId; }
    public String getExternalCode() { return externalCode; }
    public String getDatasetCode() { return datasetCode; }
    public String getOriginalName() { return originalName; }
    public String getMethodology() { return methodology; }
    public String getSourceOrganization() { return sourceOrganization; }
    public String getUnit() { return unit; }
    public String getFrequency() { return frequency; }
}
