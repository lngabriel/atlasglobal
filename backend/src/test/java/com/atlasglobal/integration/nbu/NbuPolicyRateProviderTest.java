package com.atlasglobal.integration.nbu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import com.atlasglobal.integration.RateHttp;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NbuPolicyRateProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private com.fasterxml.jackson.databind.JsonNode fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/policy-rates/nbu-daily.json")) { return mapper.readTree(input); }
    }
    @Test
    void boundsHistoricalRequestsToOneYearWithoutGapsOrOverlaps() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://bank.gov.ua/NBUStatService/v1/statdirectory/key?start=20240229&end=20250227&json"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://bank.gov.ua/NBUStatService/v1/statdirectory/key?start=20250228&end=20260227&json"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://bank.gov.ua/NBUStatService/v1/statdirectory/key?start=20260228&end=20260301&json"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        var provider = new NbuPolicyRateProvider(new RateHttp(builder.build(), mapper, 1, Duration.ZERO));
        assertThat(provider.load(LocalDate.parse("2024-02-29"), LocalDate.parse("2026-03-01")).getFirst().points()).isEmpty();
        server.verify();
    }
    @Test
    void selectsOnlyTheOfficialPolicyInstrumentAndPreservesDailyDates() throws Exception {
        var result = NbuPolicyRateProvider.decode(fixture(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-10"));
        assertThat(result.referenceArea()).isEqualTo("UA");
        assertThat(result.points()).hasSize(10);
        assertThat(result.points().getLast().date()).isEqualTo(LocalDate.parse("2026-11-10"));
        assertThat(result.points().getLast().value()).isEqualByComparingTo("16.5");
        assertThat(result.externalCode()).isEqualTo("KEY_PolicyRate");
    }
    @Test
    void rejectsChangedDefinitionsAndDatesOutsideTheRequestedRange() throws Exception {
        var root = fixture();
        for (var row : root) if (row.path("id_api").asText().equals("KEY_PolicyRate")) ((ObjectNode)row).put("freq", "M");
        assertThatThrownBy(() -> NbuPolicyRateProvider.decode(root, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-10")))
            .hasMessageContaining("definition");
        assertThatThrownBy(() -> NbuPolicyRateProvider.decode(fixture(), LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-10")))
            .hasMessageContaining("scope");
    }
}
