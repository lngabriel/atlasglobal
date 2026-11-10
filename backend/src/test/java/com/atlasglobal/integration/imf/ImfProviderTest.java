package com.atlasglobal.integration.imf;

import com.atlasglobal.ingestion.ImportProperties;
import com.atlasglobal.integration.RateHttp;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ImfProviderTest {
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final ImfCatalog.Entry debt = ImfCatalog.ENTRIES.getFirst();
    private final ImportProperties settings = new ImportProperties(List.of("BRA", "USA", "TWN", "VEN", "XKX"), 2024, 2026);

    private JsonNode fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/imf/" + name + ".json")) { return mapper.readTree(input); }
    }
    private JsonNode metadata() throws Exception { return fixture("indicators").path("indicators").path(debt.externalCode()); }
    private JsonNode countries() throws Exception { return fixture("countries").path("countries"); }

    @Test
    void retainsOfficialHistoryProjectionsAndReleaseIdentityWithoutMixingAggregates() throws Exception {
        var data = ImfProvider.decode(debt, metadata(), countries(), fixture("government-debt"), settings);
        assertThat(data.definition().code()).isEqualTo("IMF.GOVERNMENT.DEBT.GDP");
        assertThat(data.definition().datasetCode()).isEqualTo("WEO:2026-10");
        assertThat(data.definition().unit()).isEqualTo("PERCENT_GDP");
        assertThat(data.metadata().externalCode()).isEqualTo("GGXWDG_NGDP");
        assertThat(data.metadata().methodology()).contains("staff estimates", "IMF_PROJECTION", "2026");
        assertThat(data.batch().sourceUpdatedAt()).isNull();
        assertThat(data.batch().observations()).hasSize(12).allSatisfy(row -> {
            assertThat(row.year()).isBetween(2024, 2026);
            assertThat(row.indicatorCode()).isEqualTo(debt.code());
            assertThat(row.countryCode()).isIn("BRA", "USA", "TWN", "XKX");
            assertThat(row.providerStatus()).isEqualTo(row.year() < 2026 ? "IMF_HISTORICAL" : "IMF_PROJECTION");
        });
        assertThat(data.batch().observations()).filteredOn(row -> row.countryCode().equals("TWN") && row.year() == 2026)
            .singleElement().satisfies(row -> assertThat(row.value()).isEqualByComparingTo("21.1"));
        assertThat(data.batch().observations()).filteredOn(row -> row.countryCode().equals("XKX") && row.year() == 2026)
            .singleElement().satisfies(row -> assertThat(row.value()).isEqualByComparingTo("18.1"));
    }

    @Test
    void preservesNegativeCurrentAccountValuesAsADistinctImfDefinition() throws Exception {
        var entry = ImfCatalog.ENTRIES.get(1);
        var data = ImfProvider.decode(entry, fixture("indicators").path("indicators").path(entry.externalCode()),
            countries(), fixture("current-account"), settings);
        assertThat(data.definition().code()).isEqualTo("IMF.CURRENT.ACCOUNT.GDP");
        assertThat(data.batch().observations()).filteredOn(row -> row.countryCode().equals("BRA") && row.year() == 2026)
            .singleElement().satisfies(row -> assertThat(row.value()).isEqualByComparingTo("-2.4"));
        assertThat(data.batch().observations()).filteredOn(row -> row.countryCode().equals("VEN")).hasSize(3);
    }

    @Test
    void preservesExactDecimalsZeroAndExplicitNullWithoutCreatingAbsentYears() throws Exception {
        var response = fixture("government-debt");
        var rows = (ObjectNode)response.path("values").path(debt.externalCode()).path("BRA");
        rows.put("2024", new java.math.BigDecimal("-0.1234567890123456789"));
        rows.put("2025", 0);
        rows.putNull("2026");
        rows.remove("2023");
        var data = ImfProvider.decode(debt, metadata(), countries(), response,
            new ImportProperties(List.of("BRA"), 2023, 2026));
        assertThat(data.batch().observations()).hasSize(3);
        assertThat(data.batch().observations().getFirst().value()).isEqualByComparingTo("-0.1234567890123456789");
        assertThat(data.batch().observations().get(1).value()).isZero();
        assertThat(data.batch().observations().getLast().value()).isNull();
        assertThat(data.batch().observations().getLast().providerStatus()).isEqualTo("IMF_PROJECTION");
    }

    @Test
    void rejectsMissingProjectionBoundaryIncompatibleUnitsAndUnrecognizedDefinitions() throws Exception {
        for (String field : List.of("unit", "dataset", "label", "projection-year")) {
            var changed = (ObjectNode)metadata();
            changed.remove(field);
            assertThatThrownBy(() -> ImfProvider.decode(debt, changed, countries(), fixture("government-debt"), settings))
                .hasMessageContaining("definition, unit or projection boundary");
        }
    }

    @Test
    void rejectsUnvalidatedEditionsAndMetadataChangesDuringCollection() throws Exception {
        var wrong = (ObjectNode)metadata();
        wrong.put("source", "World Economic Outlook (Unknown 2026)");
        assertThatThrownBy(() -> ImfProvider.decode(debt, wrong, countries(), fixture("government-debt"), settings))
            .hasMessageContaining("WEO edition");
        var changed = fixture("government-debt");
        ((ObjectNode)changed.path("indicators").path(debt.externalCode())).put("projection-year", 2025);
        assertThatThrownBy(() -> ImfProvider.decode(debt, metadata(), countries(), changed, settings))
            .hasMessageContaining("metadata changed");
    }

    @Test
    void keepsDifferentWEOReleasesInSeparateDatasetIdentities() throws Exception {
        var april = (ObjectNode)metadata();
        april.put("source", "World Economic Outlook (April 2026)");
        var response = fixture("government-debt");
        ((ObjectNode)response.path("indicators")).set(debt.externalCode(), april);
        assertThat(ImfProvider.decode(debt, april, countries(), response, settings).definition().datasetCode())
            .isEqualTo("WEO:2026-04");
    }

    @Test
    void rejectsMalformedYearsNumericStringsAndUnsupportedApiVersions() throws Exception {
        var wrong = fixture("government-debt");
        ((ObjectNode)wrong.path("values").path(debt.externalCode()).path("BRA")).put("2026", "NaN");
        assertThatThrownBy(() -> ImfProvider.decode(debt, metadata(), countries(), wrong, settings)).hasMessageContaining("numeric value");
        var badYear = fixture("government-debt");
        ((ObjectNode)badYear.path("values").path(debt.externalCode()).path("BRA")).put("2026-Q1", 1);
        assertThatThrownBy(() -> ImfProvider.decode(debt, metadata(), countries(), badYear, settings)).hasMessageContaining("annual period");
        var oldApi = fixture("government-debt");
        ((ObjectNode)oldApi.path("api")).put("version", "1");
        assertThatThrownBy(() -> ImfProvider.decode(debt, metadata(), countries(), oldApi, settings)).hasMessageContaining("API version");
    }

    @Test
    void loadsTheOfficialV2EndpointsAndFiltersResponsesThatIgnoreRequestedPeriods() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://www.imf.org/external/datamapper/api/v2/indicators"))
            .andRespond(withSuccess(fixture("indicators").toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.imf.org/external/datamapper/api/v2/countries"))
            .andRespond(withSuccess(fixture("countries").toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.imf.org/external/datamapper/api/v2/GGXWDG_NGDP?periods=2024,2025,2026"))
            .andRespond(withSuccess(fixture("government-debt").toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.imf.org/external/datamapper/api/v2/BCA_NGDPD?periods=2024,2025,2026"))
            .andRespond(withSuccess(fixture("current-account").toString(), MediaType.APPLICATION_JSON));
        var data = new ImfProvider(new RateHttp(builder.build(), mapper, 1, Duration.ZERO)).load(settings);
        assertThat(data).hasSize(2);
        assertThat(data).allSatisfy(series -> assertThat(series.batch().observations()).allMatch(row -> row.year() <= 2026));
        server.verify();
    }

    @Test
    void rejectsPermanentProviderFailuresWithoutReturningFixtureOrPartialData() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://www.imf.org/external/datamapper/api/v2/indicators"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> new ImfProvider(new RateHttp(builder.build(), mapper, 3, Duration.ZERO)).load(settings))
            .hasMessageContaining("HTTP failure: 403");
        server.verify();
    }
}
