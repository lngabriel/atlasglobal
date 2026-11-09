package com.atlasglobal.observation;

import com.atlasglobal.indicator.IndicatorResponse;
import com.atlasglobal.indicator.SeriesResponse;
import com.atlasglobal.shared.api.PageResponse;
import java.util.List;

public record HistoryResponse(IndicatorResponse indicator, SeriesResponse series,
                              List<CountryReference> countries, int from, int to,
                              PageResponse<ObservationResponse> observations) {
    public HistoryResponse { countries = List.copyOf(countries); }
}
