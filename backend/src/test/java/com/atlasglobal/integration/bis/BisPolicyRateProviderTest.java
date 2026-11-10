package com.atlasglobal.integration.bis;

import com.atlasglobal.integration.RateHttp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BisPolicyRateProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDate from = LocalDate.parse("2026-10-01"), to = LocalDate.parse("2026-11-10");
    private String fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/policy-rates/bis-daily.json")) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void decodesActualDailyRatesAndRetainsCountryInstrumentAndBreakMetadata() throws Exception {
        var rows = BisPolicyRateProvider.decode(mapper.readTree(fixture()), from, to);
        var us = rows.stream().filter(series -> series.referenceArea().equals("US")).findFirst().orElseThrow();
        assertThat(us.externalCode()).isEqualTo("D.US");
        assertThat(us.methodology()).contains("mid-point", "Federal Reserve");
        assertThat(us.sourceOrganization()).contains("Federal Reserve");
        assertThat(us.points().getLast().value()).isEqualByComparingTo("3.875");
        assertThat(us.points()).allMatch(point -> !point.date().isBefore(from) && !point.date().isAfter(to));
        assertThat(rows.stream().filter(series -> series.referenceArea().equals("XM")).findFirst().orElseThrow().methodology())
            .contains("deposit facility", "2024");
    }

    @Test
    void rejectsIncompatibleUnitsDefinitionsAndUnboundedDates() throws Exception {
        var data = mapper.readTree(fixture());
        ((ObjectNode)data.path("data").path("structure").path("attributes").path("dataset").get(0).path("values").get(0)).put("id", "OTHER");
        assertThatThrownBy(() -> BisPolicyRateProvider.decode(data, from, to)).hasMessageContaining("unit");
        assertThatThrownBy(() -> BisPolicyRateProvider.decode(mapper.readTree(fixture()), from.plusDays(1), to)).hasMessageContaining("date");
        var daily = mapper.readTree(fixture());
        ((ObjectNode)daily.path("data").path("structure").path("dimensions").path("series").get(0).path("values").get(0)).put("id", "M");
        assertThatThrownBy(() -> BisPolicyRateProvider.decode(daily, from, to)).hasMessageContaining("daily");
    }

    @Test
    void usesTheDocumentedV2TimeConstraintAndRetriesTransientFailures() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("c%5BTIME_PERIOD%5D=ge%3A2026-10-01%2Ble%3A2026-11-10")))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        server.expect(requestTo(containsString("/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/D.")))
            .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));
        var provider = new BisPolicyRateProvider(new RateHttp(builder.build(), mapper, 2, Duration.ZERO));
        assertThat(provider.load(from, to)).isNotEmpty();
        server.verify();
    }

    @Test
    void preservesZeroNegativeAndMissingValuesWithoutTreatingPreparationAsRevision() throws Exception {
        var root = mapper.readTree(fixture());
        var observations = (ObjectNode)root.path("data").path("dataSets").get(0).path("series").path("0:0").path("observations");
        observations.set("0", mapper.readTree("[\"0\",0,0,null]"));
        observations.set("1", mapper.readTree("[\"-0.125\",0,0,null]"));
        observations.set("2", mapper.readTree("[null,1,0,null]"));
        var au = BisPolicyRateProvider.decode(root, from, to).stream().filter(series -> series.referenceArea().equals("AU")).findFirst().orElseThrow();
        assertThat(au.points().get(0).value()).isZero();
        assertThat(au.points().get(1).value()).isEqualByComparingTo("-0.125");
        assertThat(au.points().get(2).value()).isNull();
        assertThat(au.points().get(2).providerStatus()).isEqualTo("M");
    }
}
