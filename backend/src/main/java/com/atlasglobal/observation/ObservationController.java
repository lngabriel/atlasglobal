package com.atlasglobal.observation;

import com.atlasglobal.indicator.IndicatorCategory;
import com.atlasglobal.shared.api.PageParameters;
import com.atlasglobal.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Observations", description = "Queries of persisted data without requests to external providers")
public class ObservationController {
    private final ObservationQueryService service;
    public ObservationController(ObservationQueryService service) { this.service = service; }

    @GetMapping("/countries/{iso3Code}/indicators")
    @Operation(summary = "Get country indicators", description = "One entry per series. latestAvailable identifies the latest numeric value and latestReported the latest record, including missing values. Null fields indicate absent records. Sort fields: name, code, or category.")
    public PageResponse<CountryIndicatorResponse> latest(
            @PathVariable @Pattern(regexp = "(?i)[A-Z0-9]{3}") String iso3Code,
            @RequestParam(required = false) IndicatorCategory category,
            @RequestParam(required = false) @Pattern(regexp = "(?i)[A-Z0-9_]{1,40}") String source,
            @ParameterObject @Valid @ModelAttribute PageParameters page) {
        return service.latest(iso3Code, category, source, page);
    }

    @GetMapping("/countries/{iso3Code}/indicators/{indicatorCode}")
    @Operation(summary = "Get a country indicator history", description = "Paginated annual history with unit, methodology, and source. seriesId is required when multiple series exist. Sort by period only, with country code as the tie breaker.")
    public HistoryResponse countryHistory(
            @PathVariable @Pattern(regexp = "(?i)[A-Z0-9]{3}") String iso3Code,
            @PathVariable @Pattern(regexp = "(?i)[A-Z0-9.]{1,100}") String indicatorCode,
            @RequestParam(required = false) UUID seriesId,
            @RequestParam(defaultValue = "1960") @Min(1800) @Max(2200) int from,
            @RequestParam(required = false) @Min(1800) @Max(2200) Integer to,
            @ParameterObject @Valid @ModelAttribute PageParameters page) {
        return service.history(indicatorCode, iso3Code, seriesId, from, to, page);
    }

    @GetMapping("/indicators/{indicatorCode}/history")
    @Operation(summary = "Get history for selected countries", description = "Provide one to five distinct ISO3 codes in countries. Returns original observations from the same series without aligning years or calculating comparisons. The default end year is the current UTC year. Missing values are not filled.")
    public HistoryResponse history(
            @PathVariable @Pattern(regexp = "(?i)[A-Z0-9.]{1,100}") String indicatorCode,
            @RequestParam @NotBlank @Size(max = 19) String countries,
            @RequestParam(required = false) UUID seriesId,
            @RequestParam(defaultValue = "1960") @Min(1800) @Max(2200) int from,
            @RequestParam(required = false) @Min(1800) @Max(2200) Integer to,
            @ParameterObject @Valid @ModelAttribute PageParameters page) {
        return service.history(indicatorCode, countries, seriesId, from, to, page);
    }
}
