package com.atlasglobal.indicator;

import java.util.List;

public record IndicatorDetails(IndicatorResponse indicator, List<SeriesResponse> series) {
    public IndicatorDetails { series = List.copyOf(series); }
}
