package com.atlasglobal.overview;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/overview")
@Tag(name = "World overview", description = "World aggregates published by the source, separate from country observations")
public class OverviewController {
    private final OverviewQueryService overview;

    public OverviewController(OverviewQueryService overview) { this.overview = overview; }

    @GetMapping
    @Operation(summary = "Get world highlights", description = "Returns the latest values for the WLD aggregate "
        + "published by the World Bank, with unit, year, and provenance. Does not calculate totals or averages of selected countries. "
        + "The query uses only observations already imported into PostgreSQL.")
    public OverviewResponse get() { return overview.get(); }
}
