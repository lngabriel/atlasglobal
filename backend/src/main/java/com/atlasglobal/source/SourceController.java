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
@Tag(name = "Fontes", description = "Identificação pública das fontes originais")
public class SourceController {
    private final SourceQueryService sources;

    public SourceController(SourceQueryService sources) {
        this.sources = sources;
    }

    @GetMapping
    @Operation(summary = "Pesquisar fontes", description = "Consulta os nomes e códigos das fontes, sem expor dados operacionais de importação.")
    @ApiResponse(responseCode = "200", description = "Fontes encontradas")
    @ApiResponse(responseCode = "400", description = "Parâmetro ou ordenação inválida")
    public PageResponse<SourceResponse> search(
        @RequestParam(required = false) @Size(max = 100) String q,
        @ParameterObject @Valid @ModelAttribute PageParameters page
    ) {
        return sources.search(q, page);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Consultar fonte", description = "Consulta uma fonte pelo código sem distinguir maiúsculas de minúsculas.")
    @ApiResponse(responseCode = "200", description = "Fonte encontrada")
    @ApiResponse(responseCode = "400", description = "Código da fonte inválido")
    @ApiResponse(responseCode = "404", description = "Fonte não encontrada")
    public SourceResponse getSource(@PathVariable @Pattern(regexp = "[A-Za-z0-9_]{1,40}") String code) {
        return sources.getSource(code);
    }
}
