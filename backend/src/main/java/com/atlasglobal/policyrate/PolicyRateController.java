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
@Tag(name = "Taxas básicas de juros", description = "Taxas oficiais em % ao ano, com observações diárias e metodologias identificadas")
public class PolicyRateController {
    private final PolicyRateQueryService service;
    public PolicyRateController(PolicyRateQueryService service) { this.service = service; }
    @GetMapping
    @Operation(summary = "Consultar a última taxa oficial disponível", description = "De 1 a 5 países. Cada valor mantém a própria data, instrumento e fonte; não se presume que a data de coleta seja uma decisão de juros.")
    public PolicyRateResponse.Result latest(@RequestParam @NotBlank @Size(max = 19) String countries) {
        return service.get(countries, null, null, false);
    }
    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Exportar taxas diárias em CSV", description = "Mesmos países e intervalo do histórico, sem preencher datas ausentes ou misturar instrumentos.")
    public ResponseEntity<byte[]> export(@RequestParam @NotBlank @Size(max = 19) String countries,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8;header=present"))
            .header("Content-Disposition", "attachment; filename=\"atlas-global-policy-rates.csv\"")
            .header("X-Content-Type-Options", "nosniff").body(service.csv(countries, from, to));
    }
    @GetMapping("/history")
    @Operation(summary = "Consultar ou comparar históricos datados de juros", description = "Até 5 países e 20 anos por consulta. Datas ISO 8601. Sem preenchimento de lacunas ou transformação em médias anuais; instrumentos nacionais podem diferir.")
    public PolicyRateResponse.Result history(@RequestParam @NotBlank @Size(max = 19) String countries,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.get(countries, from, to, true);
    }
}
