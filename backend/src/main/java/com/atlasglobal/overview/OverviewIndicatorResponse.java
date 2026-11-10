package com.atlasglobal.overview;

import com.atlasglobal.indicator.IndicatorResponse;
import com.atlasglobal.indicator.SeriesResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record OverviewIndicatorResponse(IndicatorResponse indicator, SeriesResponse series,
        @Schema(description = "Latest published numeric world value; null when unavailable.", nullable = true)
        WorldObservationResponse latestAvailable,
        @Schema(description = "Latest world record, including MISSING; null if there are no records.", nullable = true)
        WorldObservationResponse latestReported) {}
