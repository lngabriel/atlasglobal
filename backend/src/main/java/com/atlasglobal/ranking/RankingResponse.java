package com.atlasglobal.ranking;

import com.atlasglobal.indicator.IndicatorResponse;
import com.atlasglobal.indicator.SeriesResponse;
import com.atlasglobal.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RankingResponse(IndicatorResponse indicator, SeriesResponse series,
                              @Schema(nullable = true) Integer year, List<Integer> availableYears,
                              Coverage coverage, PageResponse<RankingEntry> ranking) {
    public RankingResponse { availableYears = List.copyOf(availableYears); }

    public record Coverage(long totalCountries, long withData, long withoutData) {}
}
