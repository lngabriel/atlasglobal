package com.atlasglobal.policyrate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Normalized dated values; provider response formats do not cross the integration boundary. */
public final class PolicyRateData {
    private PolicyRateData() {}
    public record Point(LocalDate date, BigDecimal value, String providerStatus) {}
    public record Series(String referenceArea, String externalCode, String instrument, String methodology,
                         String sourceOrganization, List<Point> points) {
        public Series { points = List.copyOf(points); }
    }
}
