package com.atlasglobal.ranking;

import com.atlasglobal.indicator.IndicatorQueryService;
import com.atlasglobal.shared.api.ApiException;
import com.atlasglobal.shared.api.PageParameters;
import com.atlasglobal.shared.api.PageResponse;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RankingQueryService {
    private final IndicatorQueryService indicators;
    private final RankingQueryRepository rankings;

    RankingQueryService(IndicatorQueryService indicators, RankingQueryRepository rankings) {
        this.indicators = indicators;
        this.rankings = rankings;
    }

    public RankingResponse get(String code, UUID seriesId, Integer year, PageParameters page) {
        var pageable = page.pageable("value", Set.of("value"), "iso3Code");
        var definition = indicators.get(code);
        var series = indicators.resolveSeries(definition, seriesId);
        if (!series.comparison().allowed()) throw ApiException.conflict(series.comparison().note());
        var years = rankings.availableYears(series.id());
        Integer selectedYear = year != null ? year : years.isEmpty() ? null : years.getFirst();
        long countries = rankings.totalCountries();
        var ranking = rankings.ranking(series.id(), selectedYear, pageable);
        long withData = ranking.getTotalElements();
        return new RankingResponse(definition.indicator(), series, selectedYear, years,
            new RankingResponse.Coverage(countries, withData, countries - withData), PageResponse.from(ranking));
    }
}
