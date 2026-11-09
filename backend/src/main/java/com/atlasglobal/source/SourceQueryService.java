package com.atlasglobal.source;

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
public class SourceQueryService {
    private static final Set<String> SORT_FIELDS = Set.of("name", "code");
    private final SourceRepository sources;

    public SourceQueryService(SourceRepository sources) {
        this.sources = sources;
    }

    public PageResponse<SourceResponse> search(String query, PageParameters page) {
        var result = sources.search(QueryText.contains(query), page.pageable("name", SORT_FIELDS, "code"));
        return PageResponse.from(result.map(SourceResponse::from));
    }

    public SourceResponse getSource(String code) {
        return sources.findByCode(code.toUpperCase(Locale.ROOT)).map(SourceResponse::from)
            .orElseThrow(() -> ApiException.notFound("Fonte não encontrada."));
    }
}
