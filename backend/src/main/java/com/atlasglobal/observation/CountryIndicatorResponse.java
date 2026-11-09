package com.atlasglobal.observation;

import com.atlasglobal.indicator.IndicatorResponse;
import com.atlasglobal.indicator.SeriesResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record CountryIndicatorResponse(IndicatorResponse indicator, SeriesResponse series,
        @Schema(description = "Último registro com valor numérico; null se nenhum estiver disponível.", nullable = true)
        ObservationResponse latestAvailable,
        @Schema(description = "Último registro recebido, inclusive MISSING; null se não houver observações.", nullable = true)
        ObservationResponse latestReported) {}
