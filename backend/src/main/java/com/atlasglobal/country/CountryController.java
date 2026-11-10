package com.atlasglobal.country;

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
@RequestMapping("/api/v1/countries")
@Tag(name = "Countries", description = "Public catalog of countries and economies")
public class CountryController {
    private final CountryQueryService countries;

    public CountryController(CountryQueryService countries) {
        this.countries = countries;
    }

    @GetMapping
    @Operation(summary = "Search countries", description = "Search by localized name, original name, ISO code, or region. "
        + "Region and income filters are combined. Search is case-insensitive and treats % and _ literally.")
    @ApiResponse(responseCode = "200", description = "Countries found")
    @ApiResponse(responseCode = "400", description = "Invalid parameter or ordering")
    public PageResponse<CountryResponse> search(
        @RequestParam(required = false) @Size(max = 100) String q,
        @RequestParam(required = false) @Size(max = 200) String region,
        @RequestParam(required = false) @Size(max = 200) String incomeLevel,
        @ParameterObject @Valid @ModelAttribute PageParameters page
    ) {
        return countries.search(q, region, incomeLevel, page);
    }

    @GetMapping("/{iso3Code}")
    @Operation(summary = "Get a country", description = "Accepts a case-insensitive three-character country code.")
    @ApiResponse(responseCode = "200", description = "Country found")
    @ApiResponse(responseCode = "400", description = "Invalid country code")
    @ApiResponse(responseCode = "404", description = "Country not found")
    public CountryResponse getCountry(@PathVariable @Pattern(regexp = "[A-Za-z0-9]{3}") String iso3Code) {
        return countries.getCountry(iso3Code);
    }
}
