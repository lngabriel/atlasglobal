package com.atlasglobal.export;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exports")
@Tag(name = "Exportação", description = "Downloads públicos de observações persistidas")
public class CsvExportController {
    private final CsvExportService service;
    public CsvExportController(CsvExportService service) { this.service = service; }

    @GetMapping(value = "/indicators", produces = "text/csv")
    @Operation(summary = "Exportar histórico completo em CSV",
        description = "De 1 a 5 países, uma série e um intervalo. Exporta todas as páginas em um único snapshot, "
            + "preservando valores, ausências e procedência. As mesmas restrições de comparação do histórico se aplicam.")
    public ResponseEntity<byte[]> export(
            @RequestParam @Pattern(regexp = "(?i)[A-Z0-9.]{1,100}") String indicator,
            @RequestParam @NotBlank @Size(max = 19) String countries,
            @RequestParam(required = false) UUID seriesId,
            @RequestParam(defaultValue = "1960") @Min(1800) @Max(2200) int from,
            @RequestParam(required = false) @Min(1800) @Max(2200) Integer to) {
        byte[] csv = service.export(indicator, countries, seriesId, from, to);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8;header=present"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("atlas-global-" + indicator.toUpperCase(Locale.ROOT) + ".csv").build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(csv);
    }
}
