package com.atlasglobal.overview;

import com.atlasglobal.indicator.IndicatorResponse;
import com.atlasglobal.indicator.SeriesResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record OverviewIndicatorResponse(IndicatorResponse indicator, SeriesResponse series,
        @Schema(description = "Último valor mundial numérico publicado; null quando indisponível.", nullable = true)
        WorldObservationResponse latestAvailable,
        @Schema(description = "Último registro mundial, inclusive MISSING; null se não houver registros.", nullable = true)
        WorldObservationResponse latestReported) {}
