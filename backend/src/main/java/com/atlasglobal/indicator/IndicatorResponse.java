package com.atlasglobal.indicator;

import java.util.UUID;

public record IndicatorResponse(UUID id, String code, String name, String description, String category) {
    static IndicatorResponse from(Indicator value) {
        return new IndicatorResponse(value.getId(), value.getCode(), value.getName(),
            value.getDescription(), value.getCategory());
    }
}
