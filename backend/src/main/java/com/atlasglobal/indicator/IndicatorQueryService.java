package com.atlasglobal.indicator;

import com.atlasglobal.shared.api.ApiException;
import com.atlasglobal.shared.api.PageParameters;
import com.atlasglobal.shared.api.PageResponse;
import com.atlasglobal.shared.api.QueryText;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IndicatorQueryService {
    private final IndicatorRepository indicators;
    private final SeriesQueryRepository series;
    public IndicatorQueryService(IndicatorRepository indicators, SeriesQueryRepository series) {
        this.indicators = indicators; this.series = series;
    }

    public PageResponse<IndicatorResponse> search(String q, IndicatorCategory category, String source, PageParameters page) {
        var pageable = page.pageable("name", Set.of("name", "code", "category"), "code");
        return PageResponse.from(indicators.search(QueryText.contains(q), category == null ? null : category.name(),
            QueryText.exact(source), pageable).map(IndicatorResponse::from));
    }

    public IndicatorDetails get(String code) {
        var indicator = indicators.findByCode(code.toUpperCase(Locale.ROOT))
            .orElseThrow(() -> ApiException.notFound("Indicador não encontrado."));
        return new IndicatorDetails(IndicatorResponse.from(indicator), series.forIndicator(indicator.getId()));
    }

    public SeriesResponse resolveSeries(IndicatorDetails details, UUID seriesId) {
        if (seriesId != null) {
            return details.series().stream().filter(s -> s.id().equals(seriesId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Série não encontrada para este indicador."));
        }
        if (details.series().size() > 1) {
            throw ApiException.conflict("Há mais de uma série para este indicador. Informe seriesId.");
        }
        if (details.series().isEmpty()) throw ApiException.notFound("Nenhuma série disponível para este indicador.");
        return details.series().getFirst();
    }
}
