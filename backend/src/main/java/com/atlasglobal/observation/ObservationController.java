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
@Tag(name = "Observações", description = "Consultas de dados persistidos, sem chamadas a fontes externas")
public class ObservationController {
    private final ObservationQueryService service;
    public ObservationController(ObservationQueryService service) { this.service = service; }

    @GetMapping("/countries/{iso3Code}/indicators")
    @Operation(summary = "Consultar indicadores de um país", description = "Uma entrada por série. latestAvailable informa o último valor numérico e latestReported o último registro, inclusive ausências. Campos nulos indicam falta de registros. Ordenação: name, code ou category.")
    public PageResponse<CountryIndicatorResponse> latest(
            @PathVariable @Pattern(regexp = "(?i)[A-Z0-9]{3}") String iso3Code,
            @RequestParam(required = false) IndicatorCategory category,
            @RequestParam(required = false) @Pattern(regexp = "(?i)[A-Z0-9_]{1,40}") String source,
            @ParameterObject @Valid @ModelAttribute PageParameters page) {
        return service.latest(iso3Code, category, source, page);
    }

    @GetMapping("/countries/{iso3Code}/indicators/{indicatorCode}")
    @Operation(summary = "Consultar histórico de um indicador no país", description = "Histórico anual paginado com unidade, metodologia e fonte. seriesId é obrigatório quando há várias séries. Ordenação apenas por period, com desempate por país.")
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
    @Operation(summary = "Consultar histórico por países", description = "Informe de 1 a 5 códigos ISO3 distintos em countries. Retorna observações originais da mesma série; não alinha anos nem calcula comparações. O ano final padrão é o atual em UTC. Dados ausentes não são preenchidos.")
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
