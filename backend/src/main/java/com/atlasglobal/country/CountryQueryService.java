package com.atlasglobal.country;

import com.atlasglobal.shared.api.ApiException;
import com.atlasglobal.shared.api.PageParameters;
import com.atlasglobal.shared.api.PageResponse;
import com.atlasglobal.shared.api.QueryText;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CountryQueryService {
    private static final Set<String> SORT_FIELDS = Set.of("name", "iso2Code", "iso3Code", "region", "incomeLevel");
    private final CountryRepository countries;

    public CountryQueryService(CountryRepository countries) {
        this.countries = countries;
    }

    public PageResponse<CountryResponse> search(String query, String region, String incomeLevel,
                                                PageParameters page) {
        var result = countries.search(QueryText.contains(query), QueryText.exact(region),
            QueryText.exact(incomeLevel), page.pageable("name", SORT_FIELDS, "iso3Code"));
        return PageResponse.from(result.map(CountryResponse::from));
    }

    public CountryResponse getCountry(String iso3Code) {
        return countries.findByIso3Code(iso3Code.toUpperCase(Locale.ROOT))
            .map(CountryResponse::from)
            .orElseThrow(() -> ApiException.notFound("País não encontrado."));
    }
}
