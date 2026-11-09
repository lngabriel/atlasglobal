package com.atlasglobal.integration.oecd;

import java.time.Duration;
import java.util.List;
import java.util.Set;
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

class OecdClientTest {
    private static final String HEADER = "DATAFLOW,REF_AREA,MEASURE,UNIT_MEASURE,PAY_PERIOD,PRICE_BASE,AGGREGATION_OPERATION,SEX,TIME_PERIOD,OBS_VALUE,BASE_PER,OBS_STATUS,UNIT_MULT,DECIMALS\r\n";
    private static final String FLOW = "OECD.ELS.SAE:DSD_EARNINGS@AV_AN_WAGE(1.0)";
    private static final OecdCatalog.Entry AVERAGE = OecdCatalog.ENTRIES.getFirst();
    private MockRestServiceServer server;
    private OecdClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl(OecdConfiguration.BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OecdClient(builder.build(), 2, Duration.ZERO);
    }

    @Test
    void preservesExactDecimalsMissingZeroWithoutInferringAnObservationUpdateDate() {
        String csv = HEADER + wage("USA", 2023, "0", "2025", "A", "0")
            + wage("USA", 2024, "", "", "M", "0")
            + wage("USA", 2025, "72018.439", "2025", "P", "0");
        server.expect(requestTo(containsString("startPeriod=2023")))
            .andRespond(withSuccess(csv, MediaType.valueOf("text/csv"))
                .header("Last-Modified", "Mon, 09 Nov 2026 10:00:00 GMT"));
        var result = client.series(AVERAGE, List.of("USA"), 2023, 2025).getFirst();
        assertThat(result.definition().unit()).isEqualTo("USD_PPP_2025_ANNUAL");
        assertThat(result.definition().datasetCode()).isEqualTo("DSD_EARNINGS@AV_AN_WAGE:2025");
        assertThat(result.batch().sourceUpdatedAt()).isNull();
        assertThat(result.batch().observations()).hasSize(3);
        assertThat(result.batch().observations().getFirst().value()).isZero();
        assertThat(result.batch().observations().get(1).value()).isNull();
        assertThat(result.batch().observations().getLast().value()).isEqualByComparingTo("72018.439");
        assertThat(result.batch().observations().getLast().providerStatus()).isEqualTo("P");
        assertThat(result.metadata().methodology()).contains("full-time", "2025", "not monthly");
        server.verify();
    }

    @Test
    void acceptsTheLiveIsoResourceDateWithoutUsingItAsAStatisticalRevisionDate() {
        server.expect(requestTo(containsString("/data/")))
            .andRespond(withSuccess(HEADER + wage("USA", 2025, "72018.439", "2025", "A", "0"), MediaType.valueOf("text/csv"))
                .header("Last-Modified", "2023-11-14"));
        var imported = client.series(AVERAGE, List.of("USA"), 2023, 2025).getFirst();
        assertThat(imported.batch().sourceUpdatedAt()).isNull();
        assertThat(imported.batch().observations().getFirst().year()).isEqualTo(2025);
        assertThat(imported.batch().observations().getFirst().value()).isEqualByComparingTo("72018.439");
        server.verify();
    }

    @Test
    void doesNotUseCollectionDateAsAnUnreportedProviderUpdateDate() {
        var result = decode(HEADER + wage("USA", 2024, "123", "2025", "A", "0"));
        assertThat(result.getFirst().batch().sourceUpdatedAt()).isNull();
    }

    @Test
    void ignoresOtherCurrenciesPeriodsAndCountriesWithoutSubstitutingThem() {
        String selected = wage("USA", 2024, "123", "2025", "A", "0");
        String csv = HEADER + selected
            + selected.replace("USD_PPP", "USD_EXC").replace(",123,", ",999,")
            + selected.replace(",A,Q,MEAN,", ",H,Q,MEAN,").replace(",123,", ",888,")
            + wage("CAN", 2024, "777", "2025", "A", "0");
        var result = decode(csv).getFirst().batch().observations();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().value()).isEqualByComparingTo("123");
    }

    @Test
    void keepsDifferentPppBaseYearsInDifferentSeries() {
        var result = decode(HEADER + wage("USA", 2024, "123", "2024", "A", "0")
            + wage("USA", 2024, "125", "2025", "A", "0"));
        assertThat(result).extracting(value -> value.definition().datasetCode())
            .containsExactly("DSD_EARNINGS@AV_AN_WAGE:2024", "DSD_EARNINGS@AV_AN_WAGE:2025");
        assertThat(result.getFirst().batch().observations()).hasSize(1);
        assertThat(result.getLast().batch().observations()).hasSize(1);
    }

    @Test
    void appliesProviderUnitMultipliersExactly() {
        var result = decode(HEADER + wage("USA", 2024, "1.23456789", "2025", "A", "3"));
        assertThat(result.getFirst().batch().observations().getFirst().value()).isEqualByComparingTo("1234.56789");
    }

    @Test
    void rejectsDuplicatesMalformedValuesAndAmbiguousPriceBases() {
        String row = wage("USA", 2024, "123", "2025", "A", "0");
        assertThatThrownBy(() -> decode(HEADER + row + row)).isInstanceOf(OecdException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> decode(HEADER + row.replace(",123,", ",NaN,")))
            .isInstanceOf(OecdException.class).hasMessageContaining("numeric");
        assertThatThrownBy(() -> decode(HEADER + row + wage("USA", 2025, "124", "", "A", "0")))
            .isInstanceOf(OecdException.class).hasMessageContaining("Ambiguous");
    }

    @Test
    void rejectsWrongDatasetOrNonannualPeriods() {
        String row = wage("USA", 2024, "123", "2025", "A", "0");
        assertThatThrownBy(() -> decode(HEADER + row.replace("AV_AN_WAGE", "RMW")))
            .isInstanceOf(OecdException.class).hasMessageContaining("dataflow");
        assertThatThrownBy(() -> decode(HEADER + row.replace(",2024,", ",2024Q1,")))
            .isInstanceOf(OecdException.class).hasMessageContaining("Nonannual");
        assertThatThrownBy(() -> decode("<html>provider error</html>"))
            .isInstanceOf(OecdException.class);
    }

    @Test
    void validatesHouseholdDebtDenominatorAndDoesNotAcceptGdpRatios() {
        var entry = OecdCatalog.ENTRIES.getLast();
        String header = "DATAFLOW,FREQ,REF_AREA,MEASURE,UNIT_MEASURE,CHAPTER,TIME_PERIOD,OBS_VALUE,OBS_STATUS,UNIT_MULT,PRICE_BASE,TRANSFORMATION,SECTOR\n";
        String row = "OECD.SDD.NAD:DSD_NAAG@DF_NAAG(1.0),A,AUS,LES1M_FD4,PT_B6N_S1M,NAAG_V,2024,198.12345,A,0,V,N,S1M\n";
        var result = OecdClient.decode(entry, header + row, Set.of("AUS"), 2024, 2024, null).getFirst();
        assertThat(result.definition().unit()).isEqualTo("PERCENT_NET_DISPOSABLE_INCOME");
        assertThat(result.batch().observations().getFirst().value()).isEqualByComparingTo("198.12345");
        assertThat(result.metadata().methodology()).contains("not debt as a percentage of GDP");
        assertThatThrownBy(() -> OecdClient.decode(entry, header + row.replace("PT_B6N_S1M", "PT_B1GQ"), Set.of("AUS"), 2024, 2024, null))
            .isInstanceOf(OecdException.class).hasMessageContaining("methodology");
    }

    @Test
    void minimumWagesUseTheirOwnAnnualStatutoryMethodology() {
        var entry = OecdCatalog.ENTRIES.get(1);
        String row = wage("USA", 2024, "15678.9", "2025", "A", "0")
            .replace("DSD_EARNINGS@AV_AN_WAGE", "DSD_EARNINGS@RMW").replace(",WG,", ",SM_WG,").replace(",MEAN,", ",_Z,");
        var result = OecdClient.decode(entry, HEADER + row, Set.of("USA"), 2024, 2024, null).getFirst();
        assertThat(result.definition().code()).isEqualTo("OECD.WAGE.MINIMUM.PPP");
        assertThat(result.metadata().methodology()).contains("Statutory", "not monthly");
    }

    @Test
    void retriesRateLimitsButRejectsPermanentHttpFailures() {
        server.expect(requestTo(containsString("/data/")))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        server.expect(requestTo(containsString("/data/")))
            .andRespond(withSuccess(HEADER + wage("USA", 2024, "123", "2025", "A", "0"), MediaType.valueOf("text/csv")));
        assertThat(client.series(AVERAGE, List.of("USA"), 2024, 2024)).hasSize(1);
        server.verify();
        server.reset();
        server.expect(requestTo(containsString("/data/"))).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.series(AVERAGE, List.of("USA"), 2024, 2024))
            .isInstanceOf(OecdException.class).hasMessage("OECD HTTP failure: 403");
        server.verify();
    }

    @Test
    void rejectsUnexpectedSourceYearsBeforeStorage() {
        assertThatThrownBy(() -> decode(HEADER + wage("USA", 2022, "123", "2025", "A", "0")))
            .isInstanceOf(OecdException.class).hasMessageContaining("outside");
        assertThatThrownBy(() -> client.series(AVERAGE, List.of("../USA"), 2024, 2024))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static String wage(String country, int year, String value, String base, String status, String multiplier) {
        return String.join(",", FLOW, country, "WG", "USD_PPP", "A", "Q", "MEAN", "_Z", String.valueOf(year), value, base, status, multiplier, "0") + "\r\n";
    }

    private static List<OecdClient.ImportedSeries> decode(String body) {
        return OecdClient.decode(AVERAGE, body, Set.of("USA"), 2023, 2025, null);
    }
}
