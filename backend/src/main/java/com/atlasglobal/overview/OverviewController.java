package com.atlasglobal.overview;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/overview")
@Tag(name = "Panorama mundial", description = "Agregados mundiais publicados pela fonte, separados dos países")
public class OverviewController {
    private final OverviewQueryService overview;

    public OverviewController(OverviewQueryService overview) { this.overview = overview; }

    @GetMapping
    @Operation(summary = "Consultar destaques mundiais", description = "Retorna os últimos valores do agregado WLD "
        + "publicado pelo Banco Mundial, com unidade, ano e procedência. Não calcula totais ou médias dos países selecionados. "
        + "A consulta utiliza apenas observações já importadas no PostgreSQL.")
    public OverviewResponse get() { return overview.get(); }
}
