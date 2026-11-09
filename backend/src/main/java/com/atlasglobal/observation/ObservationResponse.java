package com.atlasglobal.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ObservationResponse(String countryCode, int period,
        @Schema(description = "Valor original; null para MISSING. Zero é um valor disponível.", nullable = true) BigDecimal value,
        @Schema(allowableValues = {"AVAILABLE", "MISSING"}) String status,
        String providerStatus, LocalDate sourceUpdatedAt, Instant importedAt) {}
