package com.atlasglobal.export;

import com.atlasglobal.observation.CountryReference;
import com.atlasglobal.observation.HistoryResponse;
import com.atlasglobal.observation.ObservationQueryService;
import com.atlasglobal.observation.ObservationResponse;
import com.atlasglobal.shared.api.PageParameters;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CsvExportService {
    private final ObservationQueryService observations;

    public CsvExportService(ObservationQueryService observations) { this.observations = observations; }

    /** All pages share one database snapshot; errors occur before any CSV bytes are sent. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] export(String indicator, String countries, UUID seriesId, int from, Integer to) {
        PageParameters page = new PageParameters();
        page.setSize(100);
        HistoryResponse first = observations.history(indicator, countries, seriesId, from, to, page);
        Map<String, CountryReference> references = first.countries().stream()
            .collect(Collectors.toMap(CountryReference::iso3Code, Function.identity()));
        StringBuilder csv = new StringBuilder("\uFEFFcountry_code,country,indicator_code,indicator,period,value,unit,source_code,source,series_id,dataset_code,status,provider_status,source_updated_at,collected_at\r\n");
        append(csv, first, references);
        for (int index = 1; index < first.observations().totalPages(); index++) {
            page.setPage(index);
            append(csv, observations.history(indicator, countries, first.series().id(), first.from(), first.to(), page), references);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void append(StringBuilder csv, HistoryResponse history, Map<String, CountryReference> countries) {
        for (ObservationResponse row : history.observations().content()) {
            csv.append(text(row.countryCode())).append(',')
                .append(text(countries.get(row.countryCode()).name())).append(',')
                .append(text(history.indicator().code())).append(',').append(text(history.indicator().name())).append(',')
                .append(row.period()).append(',').append(row.value() == null ? "" : row.value().toPlainString()).append(',')
                .append(text(history.series().unit())).append(',').append(text(history.series().source().code())).append(',')
                .append(text(history.series().source().name())).append(',').append(text(history.series().id().toString())).append(',')
                .append(text(history.series().datasetCode())).append(',').append(text(row.status())).append(',')
                .append(text(row.providerStatus())).append(',')
                .append(text(row.sourceUpdatedAt() == null ? null : row.sourceUpdatedAt().toString())).append(',')
                .append(text(row.importedAt() == null ? null : row.importedAt().toString())).append("\r\n");
        }
    }

    /** RFC 4180 quoting plus a spreadsheet text prefix for formula-like textual cells. */
    static String text(String value) {
        if (value == null) return "\"\"";
        String leading = value.stripLeading();
        boolean control = !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0;
        boolean formula = !leading.isEmpty() && "=+-@＝＋－＠".indexOf(leading.charAt(0)) >= 0;
        String safe = control || formula ? "\t" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
