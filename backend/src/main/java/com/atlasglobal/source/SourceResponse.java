package com.atlasglobal.source;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Metadados públicos da fonte original dos dados")
public record SourceResponse(UUID id, String code, String name, String baseUrl,
                             String description, String documentationUrl) {
    public static SourceResponse from(DataSource source) {
        return new SourceResponse(source.getId(), source.getCode(), source.getName(), source.getBaseUrl(),
            source.getDescription(), source.getDocumentationUrl());
    }
}
