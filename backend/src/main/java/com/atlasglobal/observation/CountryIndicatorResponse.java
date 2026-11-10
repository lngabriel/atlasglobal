package com.atlasglobal.observation;

import com.atlasglobal.indicator.IndicatorResponse;
import com.atlasglobal.indicator.SeriesResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record CountryIndicatorResponse(IndicatorResponse indicator, SeriesResponse series,
        @Schema(description = "Latest record with a numeric value; null if none is available.", nullable = true)
        ObservationResponse latestAvailable,
        @Schema(description = "Latest received record, including MISSING; null if there are no observations.", nullable = true)
        ObservationResponse latestReported) {}
