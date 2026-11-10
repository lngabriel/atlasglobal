package com.atlasglobal.integration.bcb;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BcbClientTest {
    private MockRestServiceServer server;
    private BcbClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl(BcbConfiguration.BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BcbClient(builder.build(), new ObjectMapper(), 2, Duration.ZERO,
            Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void importsTheSelicTargetAsDatedDailyRatesWithoutYearEndSelection() {
        server.expect(requestTo(containsString("bcdata.sgs.432/dados")))
            .andRespond(withSuccess("[{\"data\":\"01/11/2026\",\"valor\":\"0\"},{\"data\":\"02/11/2026\",\"valor\":\"13.75\"},{\"data\":\"03/11/2026\",\"valor\":null}]", MediaType.APPLICATION_JSON));
        var points = client.policyRate(java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2026-11-03"));
        assertThat(points).hasSize(3);
        assertThat(points.getFirst().value()).isZero();
        assertThat(points.get(1).date()).isEqualTo(java.time.LocalDate.parse("2026-11-02"));
        assertThat(points.get(1).value()).isEqualByComparingTo("13.75");
        assertThat(points.getLast().value()).isNull();
        server.verify();
    }

    @Test
    void selectsDecemberWithoutAveragingOrSubstitutingAnotherMonth() {
        var rows = client.decode("""
            [{"data":"01/11/2024","valor":"999.99"},
             {"data":"01/12/2024","valor":"48.45"},
             {"data":"01/08/2025","valor":"51.1234567"},
             {"data":"01/12/2025","valor":"49.70"}]
            """, 2024, 2025);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.year()).containsExactly(2024, 2025);
        assertThat(rows.getFirst().countryCode()).isEqualTo("BRA");
        assertThat(rows.getLast().value()).isEqualByComparingTo("49.70");
        assertThat(rows.getLast().indicatorCode()).isEqualTo("BCB.HOUSEHOLD.DEBT.DECEMBER");
        assertThat(BcbCatalog.METADATA.methodology()).contains("December", "restricted gross", "not spliced", "not a cross-country comparison");
    }

    @Test
    void preservesExactDecimalsZeroAndExplicitNullWithoutFillingMissingDecembers() {
        var rows = client.decode("""
            [{"data":"01/12/2020","valor":"0"},
             {"data":"01/12/2021","valor":null},
             {"data":"01/11/2022","valor":"45.0"},
             {"data":"01/12/2023","valor":"47.7500123456789"}]
            """, 2020, 2023);
        assertThat(rows).hasSize(3);
        assertThat(rows.getFirst().value()).isZero();
        assertThat(rows.get(1).value()).isNull();
        assertThat(rows.getLast().value()).isEqualByComparingTo("47.7500123456789");
        assertThat(rows).noneMatch(row -> row.year() == 2022);
        assertThat(rows).allMatch(row -> row.providerStatus() == null);
    }

    @Test
    void keepsDecember2025AsLatestCompleteYearInsteadOfPartial2026() {
        server.expect(requestTo(containsString("dataFinal=31/12/2025")))
            .andRespond(withSuccess("[{\"data\":\"01/12/2025\",\"valor\":\"49.70\"}]", MediaType.APPLICATION_JSON)
                .header("Last-Modified", "Mon, 09 Nov 2026 10:00:00 GMT"));
        var result = client.series(2025, 2026);
        assertThat(result.observations()).hasSize(1);
        assertThat(result.observations().getFirst().year()).isEqualTo(2025);
        assertThat(result.sourceUpdatedAt()).isNull();
        server.verify();
    }

    @Test
    void boundsRequestsToTenYearsAndNeverRequestsBeforeThePublishedStart() {
        server.expect(requestTo(containsString("dataInicial=01/01/2005&dataFinal=31/12/2014")))
            .andRespond(withSuccess("[{\"data\":\"01/12/2005\",\"valor\":\"20.01\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("dataInicial=01/01/2015&dataFinal=31/12/2024")))
            .andRespond(withSuccess("[{\"data\":\"01/12/2024\",\"valor\":\"48.45\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("dataInicial=01/01/2025&dataFinal=31/12/2025")))
            .andRespond(withSuccess("[{\"data\":\"01/12/2025\",\"valor\":\"49.70\"}]", MediaType.APPLICATION_JSON));
        var result = client.series(1960, 2026);
        assertThat(result.observations()).extracting(row -> row.year()).containsExactly(2005, 2024, 2025);
        server.verify();
    }

    @Test
    void rejectsMalformedCollectionsDatesDuplicatesAndNumbers() {
        assertThatThrownBy(() -> client.decode("{\"error\":\"provider error\"}", 2024, 2025))
            .isInstanceOf(BcbException.class).hasMessageContaining("collection");
        assertThatThrownBy(() -> client.decode("<html>service unavailable</html>", 2024, 2025))
            .isInstanceOf(BcbException.class).hasMessageContaining("JSON");
        assertThatThrownBy(() -> client.decode("[{\"data\":\"01/12/2024\"}]", 2024, 2025))
            .isInstanceOf(BcbException.class).hasMessageContaining("fields");
        assertThatThrownBy(() -> client.decode("[{\"data\":\"31/02/2024\",\"valor\":\"12\"}]", 2024, 2025))
            .isInstanceOf(BcbException.class).hasMessageContaining("date");
        String row = "{\"data\":\"01/12/2024\",\"valor\":\"48.45\"}";
        assertThatThrownBy(() -> client.decode("[" + row + "," + row + "]", 2024, 2025))
            .isInstanceOf(BcbException.class).hasMessageContaining("Duplicate");
        for (String value : new String[] {"NaN", "48,45", "", "-1", "1e309"}) {
            assertThatThrownBy(() -> client.decode("[" + row.replace("48.45", value) + "]", 2024, 2025))
                .isInstanceOf(BcbException.class).hasMessageContaining("numeric");
        }
    }

    @Test
    void rejectsOutOfScopeAndNonmonthlyDatesBeforeStorage() {
        assertThatThrownBy(() -> client.decode("[{\"data\":\"01/12/2026\",\"valor\":\"50\"}]", 2024, 2025))
            .isInstanceOf(BcbException.class).hasMessageContaining("outside");
        assertThatThrownBy(() -> client.decode("[{\"data\":\"31/12/2025\",\"valor\":\"50\"}]", 2024, 2025))
            .isInstanceOf(BcbException.class).hasMessageContaining("monthly");
        assertThatThrownBy(() -> client.series(2025, 2024)).isInstanceOf(IllegalArgumentException.class);
        assertThat(client.series(1960, 2004).observations()).isEmpty();
        assertThat(client.series(2026, 2026).observations()).isEmpty();
    }

    @Test
    void retriesRateLimitsButRejectsPermanentFailuresAndExcessiveBackoff() {
        server.expect(requestTo(containsString("bcdata.sgs.29037")))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        server.expect(requestTo(containsString("bcdata.sgs.29037")))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.series(2025, 2025).observations()).isEmpty();
        server.verify();
        server.reset();
        server.expect(requestTo(containsString("bcdata.sgs.29037"))).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.series(2025, 2025)).isInstanceOf(BcbException.class).hasMessage("BCB HTTP failure: 403");
        server.verify();
        server.reset();
        server.expect(requestTo(containsString("bcdata.sgs.29037")))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "61"));
        assertThatThrownBy(() -> client.series(2025, 2025)).isInstanceOf(BcbException.class).hasMessageContaining("longer retry interval");
        server.verify();
    }
}
