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
@Tag(name = "Países", description = "Catálogo público de países e economias")
public class CountryController {
    private final CountryQueryService countries;

    public CountryController(CountryQueryService countries) {
        this.countries = countries;
    }

    @GetMapping
    @Operation(summary = "Pesquisar países", description = "Busca por nome localizado, nome original, ISO ou região. "
        + "Os filtros de região e renda são combinados. A pesquisa ignora maiúsculas e minúsculas e trata % e _ literalmente.")
    @ApiResponse(responseCode = "200", description = "Países encontrados")
    @ApiResponse(responseCode = "400", description = "Parâmetro ou ordenação inválida")
    public PageResponse<CountryResponse> search(
        @RequestParam(required = false) @Size(max = 100) String q,
        @RequestParam(required = false) @Size(max = 200) String region,
        @RequestParam(required = false) @Size(max = 200) String incomeLevel,
        @ParameterObject @Valid @ModelAttribute PageParameters page
    ) {
        return countries.search(q, region, incomeLevel, page);
    }

    @GetMapping("/{iso3Code}")
    @Operation(summary = "Consultar país", description = "Aceita o código de três caracteres sem distinguir maiúsculas de minúsculas.")
    @ApiResponse(responseCode = "200", description = "País encontrado")
    @ApiResponse(responseCode = "400", description = "Código de país inválido")
    @ApiResponse(responseCode = "404", description = "País não encontrado")
    public CountryResponse getCountry(@PathVariable @Pattern(regexp = "[A-Za-z0-9]{3}") String iso3Code) {
        return countries.getCountry(iso3Code);
    }
}
