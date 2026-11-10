package com.atlasglobal.policyrate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policy-rates")
@Tag(name = "Policy rates", description = "Official rates in percent per year, with daily observations and identified methodologies")
public class PolicyRateController {
    private final PolicyRateQueryService service;
    public PolicyRateController(PolicyRateQueryService service) { this.service = service; }
    @GetMapping
    @Operation(summary = "Get the latest available official rate", description = "One to five countries. Each value retains its own date, instrument, and source; collection dates are not assumed to be policy-decision dates.")
    public PolicyRateResponse.Result latest(@RequestParam @NotBlank @Size(max = 19) String countries) {
        return service.get(countries, null, null, false);
    }
    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export daily rates as CSV", description = "Uses the same countries and interval as the history endpoint, without filling absent dates or mixing instruments.")
    public ResponseEntity<byte[]> export(@RequestParam @NotBlank @Size(max = 19) String countries,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8;header=present"))
            .header("Content-Disposition", "attachment; filename=\"atlas-global-policy-rates.csv\"")
            .header("X-Content-Type-Options", "nosniff").body(service.csv(countries, from, to));
    }
    @GetMapping("/history")
    @Operation(summary = "Get or compare dated policy-rate histories", description = "Up to five countries and twenty years per request. ISO 8601 dates. No gap filling or conversion to annual averages; national instruments may differ.")
    public PolicyRateResponse.Result history(@RequestParam @NotBlank @Size(max = 19) String countries,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.get(countries, from, to, true);
    }
}
