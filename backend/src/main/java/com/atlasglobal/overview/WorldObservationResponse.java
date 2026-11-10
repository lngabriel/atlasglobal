package com.atlasglobal.overview;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record WorldObservationResponse(int period,
        @Schema(description = "Published world aggregate value; null for MISSING.", nullable = true)
        BigDecimal value,
        @Schema(allowableValues = {"AVAILABLE", "MISSING"}) String status,
        String providerStatus, LocalDate sourceUpdatedAt, Instant importedAt) {}
