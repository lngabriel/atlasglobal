package com.atlasglobal.integration.nbu;

import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.integration.RateHttp;
import com.atlasglobal.policyrate.PolicyRateData;
import com.atlasglobal.policyrate.PolicyRateProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Official central-bank fallback for Ukraine, which has no WS_CBPOL series in the checked BIS release. */
@Service
public class NbuPolicyRateProvider implements PolicyRateProvider {
    private final RateHttp http;
    @Autowired public NbuPolicyRateProvider(ObjectMapper mapper) { this(RateHttp.create(mapper, "application/json")); }
    NbuPolicyRateProvider(RateHttp http) { this.http = http; }
    public ProviderData.Source source() {
        return new ProviderData.Source("NBU", "National Bank of Ukraine", "https://bank.gov.ua/NBUStatService/v1/statdirectory/",
            "Official NBU key policy rate", "https://bank.gov.ua/en/monetary/stages/archive-rish");
    }
    public long lockKey() { return 728301952152L; }
    public List<PolicyRateData.Series> load(LocalDate from, LocalDate to) {
        var points = new ArrayList<PolicyRateData.Point>();
        for (LocalDate start = from; !start.isAfter(to); start = start.plusYears(1)) {
            LocalDate end = start.plusYears(1).minusDays(1).isBefore(to) ? start.plusYears(1).minusDays(1) : to;
            URI uri = URI.create("https://bank.gov.ua/NBUStatService/v1/statdirectory/key?start="
                + start.format(DateTimeFormatter.BASIC_ISO_DATE) + "&end=" + end.format(DateTimeFormatter.BASIC_ISO_DATE) + "&json");
            points.addAll(decode(http.get(uri), start, end).points());
        }
        return List.of(definition(points));
    }
    static PolicyRateData.Series decode(JsonNode rows, LocalDate from, LocalDate to) {
        if (!rows.isArray() || rows.size() > 100_000) throw new IllegalStateException("Invalid NBU policy-rate collection");
        var points = new ArrayList<PolicyRateData.Point>();
        var dates = new HashSet<LocalDate>();
        for (JsonNode row : rows) {
            if (!"KEY_PolicyRate".equals(row.path("id_api").asText())) continue;
            if (!"D".equals(row.path("freq").asText()) || !"T90".equals(row.path("tzep").asText())
                || !"NBU key policy rate".equals(row.path("txten").asText().strip()) || !row.has("value")) {
                throw new IllegalStateException("NBU policy-rate definition changed");
            }
            LocalDate date = LocalDate.parse(row.path("dt").asText(), DateTimeFormatter.BASIC_ISO_DATE);
            if (date.isBefore(from) || date.isAfter(to) || !dates.add(date)) throw new IllegalStateException("Duplicate or out-of-scope NBU date");
            JsonNode raw = row.get("value");
            if (!raw.isNull() && (!raw.isNumber() || !raw.asText().matches("-?[0-9]+(?:\\.[0-9]+)?"))) {
                throw new IllegalStateException("Invalid NBU policy-rate value");
            }
            points.add(new PolicyRateData.Point(date, raw.isNull() ? null : new BigDecimal(raw.asText()), null));
        }
        points.sort(Comparator.comparing(PolicyRateData.Point::date));
        return definition(points);
    }
    private static PolicyRateData.Series definition(List<PolicyRateData.Point> points) {
        return new PolicyRateData.Series("UA", "KEY_PolicyRate", "NBU key policy rate",
            "Daily key policy rate published directly by the National Bank of Ukraine, in percent per year. "
                + "Overnight deposit and lending facilities returned by the same endpoint are excluded. "
                + "The reference date is preserved; the API's date is not assumed to be the last policy-decision date. "
                + "https://bank.gov.ua/NBUStatService/v1/statdirectory/key?json", "National Bank of Ukraine", points);
    }
}
