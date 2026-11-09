package com.atlasglobal.indicator;

import com.atlasglobal.shared.api.PageParameters;
import com.atlasglobal.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/indicators")
@Tag(name = "Indicadores", description = "Catálogo e definições de séries disponíveis")
public class IndicatorController {
    private final IndicatorQueryService service;
    public IndicatorController(IndicatorQueryService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Pesquisar indicadores", description = "Filtros combinados; source é o código público da fonte. Ordenação: name, code ou category.")
    public PageResponse<IndicatorResponse> search(@RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) IndicatorCategory category,
            @RequestParam(required = false) @Pattern(regexp = "(?i)[A-Z0-9_]{1,40}") String source,
            @ParameterObject @Valid @ModelAttribute PageParameters page) {
        return service.search(q, category, source, page);
    }

    @GetMapping("/{indicatorCode}")
    @Operation(summary = "Consultar indicador e séries", description = "Cada série identifica sua unidade, frequência, metodologia e fonte.")
    public IndicatorDetails get(@PathVariable @Pattern(regexp = "(?i)[A-Z0-9.]{1,100}") String indicatorCode) {
        return service.get(indicatorCode);
    }
}
