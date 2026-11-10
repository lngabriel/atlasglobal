package com.atlasglobal.ranking;

import com.atlasglobal.shared.api.PageParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/indicators")
@Tag(name = "Rankings", description = "Rankings for the same year, series, unit, and source")
public class RankingController {
    private final RankingQueryService service;

    public RankingController(RankingQueryService service) { this.service = service; }

    @GetMapping("/{indicatorCode}/ranking")
    @Operation(summary = "Get an indicator ranking",
        description = "One year and one series. When year is omitted, selects the latest year with numeric values. "
            + "Coverage reports how many catalog countries have data for that year; missing values are not converted to zero. "
            + "Ties share a rank, with stable display ordering by ISO3. Sort by value only; "
            + "direction accepts asc or desc. Definitions unsuitable for comparison return 409.")
    public RankingResponse get(
            @PathVariable @Pattern(regexp = "(?i)[A-Z0-9.]{1,100}") String indicatorCode,
            @RequestParam(required = false) UUID seriesId,
            @RequestParam(required = false) @Min(1800) @Max(2200) Integer year,
            @ParameterObject @Valid @ModelAttribute PageParameters page) {
        return service.get(indicatorCode, seriesId, year, page);
    }
}
