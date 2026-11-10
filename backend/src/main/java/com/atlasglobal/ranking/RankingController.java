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
@Tag(name = "Rankings", description = "Classificações de um mesmo ano, série, unidade e fonte")
public class RankingController {
    private final RankingQueryService service;

    public RankingController(RankingQueryService service) { this.service = service; }

    @GetMapping("/{indicatorCode}/ranking")
    @Operation(summary = "Consultar ranking de um indicador",
        description = "Um único ano e série. Sem year, seleciona o ano mais recente com valores numéricos. "
            + "A cobertura informa quantos países do catálogo têm dados nesse ano; ausências não viram zero. "
            + "Empates compartilham posição, com desempate visual estável por ISO3. Ordenação apenas por value; "
            + "direction aceita asc ou desc. Definições incompatíveis com comparação retornam 409.")
    public RankingResponse get(
            @PathVariable @Pattern(regexp = "(?i)[A-Z0-9.]{1,100}") String indicatorCode,
            @RequestParam(required = false) UUID seriesId,
            @RequestParam(required = false) @Min(1800) @Max(2200) Integer year,
            @ParameterObject @Valid @ModelAttribute PageParameters page) {
        return service.get(indicatorCode, seriesId, year, page);
    }
}
