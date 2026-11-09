package com.atlasglobal.indicator;

import com.atlasglobal.source.SourceResponse;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SeriesResponse(UUID id, String externalCode, String datasetCode, String originalName,
                             String methodology, String sourceOrganization, String unit,
                             String frequency, SourceResponse source) {
    @JsonProperty("comparison")
    public SeriesComparison comparison() { return SeriesComparison.forSeries(this); }
}
