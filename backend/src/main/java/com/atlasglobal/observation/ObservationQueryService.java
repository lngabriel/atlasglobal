package com.atlasglobal.observation;

import com.atlasglobal.indicator.IndicatorCategory;
import com.atlasglobal.indicator.IndicatorQueryService;
import com.atlasglobal.shared.api.ApiException;
import com.atlasglobal.shared.api.PageParameters;
import com.atlasglobal.shared.api.PageResponse;
import com.atlasglobal.shared.api.QueryText;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ObservationQueryService {
    private final ObservationQueryRepository observations;
    private final IndicatorQueryService indicators;
    public ObservationQueryService(ObservationQueryRepository observations, IndicatorQueryService indicators) {
        this.observations = observations; this.indicators = indicators;
    }

    public HistoryResponse history(String code, String countries, UUID seriesId, int from, Integer to, PageParameters page) {
        var countryCodes = parseCountries(countries);
        var references = observations.countries(countryCodes);
        if (references.size() != countryCodes.size()) throw ApiException.notFound("Um ou mais países não foram encontrados.");
        int end = to == null ? Year.now(ZoneOffset.UTC).getValue() : to;
        if (from > end) throw ApiException.badRequest("O ano inicial deve ser menor ou igual ao ano final.");
        var pageable = page.pageable("period", Set.of("period"), "countryCode");
        var indicator = indicators.get(code);
        var series = indicators.resolveSeries(indicator, seriesId);
        if (countryCodes.size() > 1 && !series.comparison().allowed()) {
            throw ApiException.conflict(series.comparison().note());
        }
        return new HistoryResponse(indicator.indicator(), series, references, from, end,
            PageResponse.from(observations.history(series.id(), countryCodes, from, end, pageable)));
    }

    public PageResponse<CountryIndicatorResponse> latest(String country, IndicatorCategory category,
                                                         String source, PageParameters page) {
        String code = country.toUpperCase(Locale.ROOT);
        if (observations.countries(List.of(code)).isEmpty()) throw ApiException.notFound("País não encontrado.");
        return PageResponse.from(observations.latest(code, category == null ? null : category.name(), QueryText.exact(source),
            page.pageable("name", Set.of("name", "code", "category"), "code")));
    }

    private List<String> parseCountries(String countries) {
        var codes = Arrays.stream(countries.split(",", -1)).map(String::strip)
            .map(code -> code.toUpperCase(Locale.ROOT)).toList();
        if (codes.isEmpty() || codes.size() > 5 || codes.stream().anyMatch(c -> !c.matches("[A-Z0-9]{3}"))
            || codes.stream().distinct().count() != codes.size()) {
            throw ApiException.badRequest("Informe de um a cinco códigos de países distintos, separados por vírgulas.");
        }
        return codes;
    }
}
