package com.atlasglobal.source;

import com.atlasglobal.shared.api.PageParameters;
import com.atlasglobal.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sources")
@Tag(name = "Sources", description = "Public identification of original data sources")
public class SourceController {
    private final SourceQueryService sources;

    public SourceController(SourceQueryService sources) {
        this.sources = sources;
    }

    @GetMapping
    @Operation(summary = "Search sources", description = "Searches source names and codes without exposing operational import data.")
    @ApiResponse(responseCode = "200", description = "Sources found")
    @ApiResponse(responseCode = "400", description = "Invalid parameter or ordering")
    public PageResponse<SourceResponse> search(
        @RequestParam(required = false) @Size(max = 100) String q,
        @ParameterObject @Valid @ModelAttribute PageParameters page
    ) {
        return sources.search(q, page);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get a source", description = "Retrieves a source by its case-insensitive code.")
    @ApiResponse(responseCode = "200", description = "Source found")
    @ApiResponse(responseCode = "400", description = "Invalid source code")
    @ApiResponse(responseCode = "404", description = "Source not found")
    public SourceResponse getSource(@PathVariable @Pattern(regexp = "[A-Za-z0-9_]{1,40}") String code) {
        return sources.getSource(code);
    }
}
