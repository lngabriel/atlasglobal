package com.atlasglobal.policyrate;

import com.atlasglobal.observation.CountryReference;
import com.atlasglobal.shared.api.ApiException;
import com.atlasglobal.shared.api.CountrySelection;
import com.atlasglobal.shared.api.CsvText;
import com.atlasglobal.source.SourceResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PolicyRateQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    public PolicyRateQueryService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public byte[] csv(String codes, LocalDate from, LocalDate to) {
        var data = get(codes, from, to, true);
        var countries = data.countries().stream().collect(Collectors.toMap(item -> item.country().iso3Code(), Function.identity()));
        StringBuilder csv = new StringBuilder("\uFEFFcountry_code,country,indicator,period,value,unit,frequency,source_code,source,instrument,external_code,status,provider_status,source_updated_at,collected_at\r\n");
        for (var row : data.observations()) {
            var country = countries.get(row.countryCode());
            var definition = country.series();
            csv.append(CsvText.cell(row.countryCode())).append(',').append(CsvText.cell(country.country().name())).append(',')
                .append(CsvText.cell(data.code())).append(',').append(row.date()).append(',')
                .append(row.value() == null ? "" : row.value().toPlainString()).append(',').append(CsvText.cell(data.unit())).append(',')
                .append(CsvText.cell(definition.frequency())).append(',').append(CsvText.cell(definition.source().code())).append(',')
                .append(CsvText.cell(definition.source().name())).append(',').append(CsvText.cell(definition.instrument())).append(',')
                .append(CsvText.cell(definition.externalCode())).append(',').append(CsvText.cell(row.status())).append(',')
                .append(CsvText.cell(row.providerStatus())).append(',').append(CsvText.cell(row.sourceUpdatedAt() == null ? null : row.sourceUpdatedAt().toString())).append(',')
                .append(CsvText.cell(row.importedAt().toString())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public PolicyRateResponse.Result get(String codes, LocalDate from, LocalDate to, boolean history) {
        List<String> countries = CountrySelection.parse(codes);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusYears(1) : from;
        if (start.isAfter(end) || start.isBefore(LocalDate.of(1900, 1, 1)) || end.isAfter(today)
            || start.plusYears(20).isBefore(end)) throw ApiException.badRequest("Informe um intervalo de até 20 anos, sem datas futuras.");
        var parameters = new MapSqlParameterSource("countries", countries).addValue("today", today);
        var summaries = jdbc.query("""
            SELECT c.iso3_code,c.name AS country_name,s.id AS series_id,s.external_code,s.frequency,s.instrument,
                s.methodology,s.source_organization,s.scope_note,d.id AS source_id,d.code AS source_code,
                d.name AS source_name,d.base_url,d.description AS source_description,d.documentation_url,
                o.observation_date,o.value,o.status,o.provider_status,o.source_updated_at,o.imported_at
            FROM country c
            LEFT JOIN LATERAL (
                SELECT p.* FROM policy_rate_series p JOIN data_source ds ON ds.id=p.data_source_id
                WHERE p.country_id=c.id ORDER BY CASE ds.code WHEN 'BCB' THEN 0 WHEN 'BIS' THEN 1 ELSE 2 END,p.external_code LIMIT 1
            ) s ON true LEFT JOIN data_source d ON d.id=s.data_source_id
            LEFT JOIN LATERAL (
                SELECT r.* FROM policy_rate_observation r WHERE r.series_id=s.id AND r.status='AVAILABLE'
                    AND r.observation_date<=:today ORDER BY r.observation_date DESC LIMIT 1
            ) o ON true WHERE c.iso3_code IN (:countries) ORDER BY c.iso3_code
            """, parameters, (rs, row) -> country(rs));
        if (summaries.size() != countries.size()) throw ApiException.notFound("Um ou mais países não foram encontrados.");
        List<PolicyRateResponse.Observation> observations = List.of();
        var series = summaries.stream().filter(item -> item.series() != null).map(item -> item.series().id()).toList();
        if (history && !series.isEmpty()) {
            parameters.addValue("series", series).addValue("from", start).addValue("to", end);
            observations = jdbc.query("""
                SELECT c.iso3_code,o.* FROM policy_rate_observation o JOIN policy_rate_series s ON s.id=o.series_id
                JOIN country c ON c.id=s.country_id WHERE o.series_id IN (:series)
                    AND o.observation_date BETWEEN :from AND :to ORDER BY o.observation_date,c.iso3_code
                """, parameters, (rs, row) -> observation(rs));
        }
        return new PolicyRateResponse.Result("POLICY.RATE", "Taxa básica de juros", "PERCENT_PER_ANNUM", today,
            summaries, history ? start : null, history ? end : null, observations);
    }

    private PolicyRateResponse.CountryRate country(ResultSet rs) throws SQLException {
        String code = rs.getString("iso3_code");
        PolicyRateResponse.Definition definition = null;
        if (rs.getObject("series_id") != null) {
            definition = new PolicyRateResponse.Definition(rs.getObject("series_id", UUID.class), rs.getString("external_code"),
                rs.getString("frequency"), rs.getString("instrument"), rs.getString("methodology"), rs.getString("source_organization"),
                rs.getString("scope_note"), new SourceResponse(rs.getObject("source_id", UUID.class), rs.getString("source_code"),
                    rs.getString("source_name"), rs.getString("base_url"), rs.getString("source_description"),
                    "BCB".equals(rs.getString("source_code")) && "432".equals(rs.getString("external_code"))
                        ? "https://dadosabertos.bcb.gov.br/dataset/432-taxa-de-juros---meta-selic-definida-pelo-copom"
                        : rs.getString("documentation_url")));
        }
        var latest = rs.getObject("observation_date") == null ? null : observation(rs);
        return new PolicyRateResponse.CountryRate(new CountryReference(code, rs.getString("country_name")), definition, latest,
            latest == null ? unavailable(code) : code.equals("ARG") && definition.methodology().contains("no policy rate adopted")
                ? "Histórico publicado pelo BIS, encerrado em 2025. A fonte informa que a Argentina deixou de adotar uma taxa básica em julho de 2025; este valor não representa uma taxa vigente."
                : null);
    }

    private PolicyRateResponse.Observation observation(ResultSet rs) throws SQLException {
        var updated = rs.getDate("source_updated_at");
        return new PolicyRateResponse.Observation(rs.getString("iso3_code"), rs.getDate("observation_date").toLocalDate(),
            rs.getBigDecimal("value"), rs.getString("status"), rs.getString("provider_status"),
            updated == null ? null : updated.toLocalDate(), rs.getTimestamp("imported_at").toInstant());
    }

    private String unavailable(String code) {
        return switch (code) {
            case "SGP" -> "O MAS conduz a política monetária pelo câmbio, sem meta de juros comparável. SORA não é apresentada como taxa básica.";
            case "PRY" -> "O Banco Central do Paraguai publica a TPM, mas ainda não há uma série histórica automatizada validada nesta integração.";
            case "ARG" -> "Não há série de taxa básica integrada e validada para a Argentina. Taxas de depósitos e empréstimos não são usadas como substitutos.";
            case "TWN", "VEN", "VNM" -> "Sem série oficial integrada e validada para este país. A ausência de cobertura não representa taxa zero.";
            default -> "Sem observações integradas para este país. A disponibilidade depende da fonte e do período importado.";
        };
    }
}
