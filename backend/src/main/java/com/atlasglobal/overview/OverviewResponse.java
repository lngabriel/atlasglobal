package com.atlasglobal.overview;

import java.util.List;

public record OverviewResponse(Scope scope, List<OverviewIndicatorResponse> indicators) {
    public record Scope(String code, String name) {}
}
