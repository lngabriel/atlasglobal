package com.atlasglobal.shared.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {
    @Bean
    OpenAPI publicApi() {
        return new OpenAPI().info(new Info().title("Atlas Global API").version("v1")
            .description("Public, free queries of persisted international data. No authentication required. "
                + "Pagination is zero-based, with up to 100 records per page. Missing values remain null."))
            .servers(List.of(new Server().url("/")));
    }

    @Bean
    OpenApiCustomizer queryErrors() {
        return api -> api.getPaths().forEach((route, path) -> path.readOperations().forEach(operation -> {
            var problem = new Schema<>().type("object")
                .addProperty("type", new Schema<>().type("string"))
                .addProperty("title", new Schema<>().type("string"))
                .addProperty("status", new Schema<>().type("integer"))
                .addProperty("detail", new Schema<>().type("string"))
                .addProperty("instance", new Schema<>().type("string"));
            addError(operation, "400", "Invalid parameters", problem);
            addError(operation, "500", "Internal failure with a generic public message", problem);
            if (route.contains("{") || route.startsWith("/api/v1/exports/")) addError(operation, "404", "Resource not found", problem);
            if (route.endsWith("/history") || route.endsWith("/ranking") || route.startsWith("/api/v1/exports/") || (route.startsWith("/api/v1/countries/")
                    && route.endsWith("/indicators/{indicatorCode}"))) {
                addError(operation, "409", "Select an explicit series or review the methodological and coverage restriction", problem);
            }
        }));
    }

    private void addError(io.swagger.v3.oas.models.Operation operation, String code, String description, Schema<?> problem) {
        var existing = operation.getResponses().get(code);
        if (existing == null) operation.getResponses().addApiResponse(code, response(description, problem));
        else existing.setContent(response(description, problem).getContent());
    }

    private ApiResponse response(String description, Schema<?> problem) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
            new io.swagger.v3.oas.models.media.MediaType().schema(problem)));
    }
}
