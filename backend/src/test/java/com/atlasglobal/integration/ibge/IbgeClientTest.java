package com.atlasglobal.integration.ibge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPOutputStream;
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

/** Provider fixtures were captured from the public IBGE API on 2026-11-09. */
class IbgeClientTest {
    private static final IbgeCatalog.Entry GINI = IbgeCatalog.ENTRIES.getFirst();
    private static final IbgeCatalog.Entry INCOME = IbgeCatalog.ENTRIES.getLast();
    private final ObjectMapper mapper = new ObjectMapper();
    private MockRestServiceServer server;
    private IbgeClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl(IbgeConfiguration.BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IbgeClient(builder.build(), mapper, 2, Duration.ZERO);
    }

    @Test
    void preservesThePublishedGiniCoefficientWithoutConvertingToWorldBankScale() throws Exception {
        var imported = decode(GINI, "gini", 2012, 2026);
        assertThat(imported.batch().observations()).hasSize(14);
        assertThat(imported.batch().observations().getLast().countryCode()).isEqualTo("BRA");
        assertThat(imported.batch().observations().getLast().value()).isEqualByComparingTo("0.511");
        assertThat(imported.definition().unit()).isEqualTo("GINI_COEFFICIENT");
        assertThat(imported.definition().datasetCode()).isEqualTo("SIDRA_7435");
        assertThat(imported.metadata().externalCode()).isEqualTo("10681");
        assertThat(imported.metadata().methodology()).contains("0–1", "fifth visits", "2020–2022");
        assertThat(imported.batch().sourceUpdatedAt()).isNull();
    }

    @Test
    void retainsThePublishedPriceBaseWhenTheRequestedHistoryEndsEarlier() throws Exception {
        var imported = decode(INCOME, "income", 2020, 2021);
        assertThat(imported.batch().observations()).extracting(value -> value.year()).containsExactly(2020, 2021);
        assertThat(imported.batch().observations().getLast().value()).isEqualByComparingTo("1692");
        assertThat(imported.definition().unit()).isEqualTo("BRL_REAL_2025_MONTHLY_PER_PERSON");
        assertThat(imported.definition().datasetCode()).isEqualTo("SIDRA_7533:2025");
        assertThat(imported.metadata().externalCode()).isEqualTo("10816.C1019.49243");
        assertThat(imported.metadata().methodology()).contains("2025", "not an annual salary", "not estimates made by Atlas");
    }

    @Test
    void createsADistinctSeriesWhenTheOfficialPublishedBaseChanges() throws Exception {
        ObjectNode metadata = fixture("income-metadata"), data = fixtureArray("income-series");
        ((ObjectNode) metadata.path("periodicidade")).put("fim", 2026);
        values(data).put("2026", "2380");
        var rebased = IbgeClient.decode(INCOME, metadata, data.path("root"), 2020, 2025);
        assertThat(rebased.definition().unit()).isEqualTo("BRL_REAL_2026_MONTHLY_PER_PERSON");
        assertThat(rebased.definition().datasetCode()).isEqualTo("SIDRA_7533:2026");
        assertThat(decode(INCOME, "income", 2020, 2025).definition().datasetCode()).isEqualTo("SIDRA_7533:2025");
    }

    @Test
    void preservesSidraAbsoluteZeroNumericZeroAndMissingSymbols() throws Exception {
        ObjectNode data = fixtureArray("gini-series");
        values(data).put("2019", "-").put("2020", "0").put("2021", "..").put("2022", "...")
            .put("2023", "X").put("2024", "0.123456789");
        var observations = IbgeClient.decode(GINI, fixture("gini-metadata"), data.path("root"), 2019, 2024).batch().observations();
        assertThat(observations.get(0).value()).isZero();
        assertThat(observations.get(0).providerStatus()).isEqualTo("-");
        assertThat(observations.get(1).value()).isZero();
        assertThat(observations.get(1).providerStatus()).isNull();
        assertThat(observations.subList(2, 5)).allSatisfy(value -> assertThat(value.value()).isNull());
        assertThat(observations.subList(2, 5)).extracting(value -> value.providerStatus()).containsExactly("..", "...", "X");
        assertThat(observations.getLast().value()).isEqualByComparingTo("0.123456789");
    }

    @Test
    void rejectsChangedTableSurveyFrequencyVariableAndUnits() throws Exception {
        for (String field : new String[]{"id", "pesquisa", "nome"}) {
            ObjectNode metadata = fixture("gini-metadata");
            metadata.put(field, "unexpected");
            assertThatThrownBy(() -> IbgeClient.decode(GINI, metadata, fixtureArray("gini-series"), 2020, 2025))
                .isInstanceOf(IbgeException.class).hasMessageContaining("definition");
        }
        ObjectNode quarterly = fixture("gini-metadata");
        ((ObjectNode) quarterly.path("periodicidade")).put("frequencia", "trimestral");
        assertThatThrownBy(() -> IbgeClient.decode(GINI, quarterly, fixtureArray("gini-series").path("root"), 2020, 2025))
            .isInstanceOf(IbgeException.class);
        ObjectNode metadata = fixture("gini-metadata");
        ((ObjectNode) metadata.path("variaveis").get(0)).put("unidade", "%");
        ObjectNode changedUnit = metadata;
        assertThatThrownBy(() -> IbgeClient.decode(GINI, changedUnit, fixtureArray("gini-series").path("root"), 2020, 2025))
            .isInstanceOf(IbgeException.class).hasMessageContaining("unit");
        ObjectNode data = fixtureArray("gini-series");
        ((ObjectNode) data.path("root").get(0)).put("unidade", "%");
        assertThatThrownBy(() -> IbgeClient.decode(GINI, fixture("gini-metadata"), data.path("root"), 2020, 2025))
            .isInstanceOf(IbgeException.class).hasMessageContaining("unit");
    }

    @Test
    void rejectsStateGeographiesAndSubpopulationCategories() throws Exception {
        ObjectNode geography = fixtureArray("income-series");
        ((ObjectNode) geography.at("/root/0/resultados/0/series/0/localidade")).put("id", "35");
        assertThatThrownBy(() -> IbgeClient.decode(INCOME, fixture("income-metadata"), geography.path("root"), 2020, 2025))
            .isInstanceOf(IbgeException.class).hasMessageContaining("geography");
        ObjectNode data = fixtureArray("income-series");
        ((ObjectNode) data.at("/root/0/resultados/0/classificacoes/0/categoria")).removeAll().put("49244", "Até o P5");
        ObjectNode subpopulation = data;
        assertThatThrownBy(() -> IbgeClient.decode(INCOME, fixture("income-metadata"), subpopulation.path("root"), 2020, 2025))
            .isInstanceOf(IbgeException.class).hasMessageContaining("population category");
    }

    @Test
    void rejectsUnexpectedYearsAndUnverifiedPriceBase() throws Exception {
        ObjectNode monthly = fixtureArray("income-series");
        values(monthly).put("202501", "2000");
        assertThatThrownBy(() -> IbgeClient.decode(INCOME, fixture("income-metadata"), monthly.path("root"), 2020, 2025))
            .isInstanceOf(IbgeException.class).hasMessageContaining("Nonannual");
        ObjectNode data = fixtureArray("income-series");
        values(data).remove("2025");
        ObjectNode missingLatest = data;
        assertThatThrownBy(() -> IbgeClient.decode(INCOME, fixture("income-metadata"), missingLatest.path("root"), 2020, 2025))
            .isInstanceOf(IbgeException.class).hasMessageContaining("disagree");
    }

    @Test
    void rejectsMalformedValuesUnknownSymbolsAndIncompatibleScales() throws Exception {
        for (String raw : new String[]{"NaN", "", "A", "0,5", "50.4", "-0.1"}) {
            ObjectNode data = fixtureArray("gini-series");
            values(data).put("2025", raw);
            assertThatThrownBy(() -> IbgeClient.decode(GINI, fixture("gini-metadata"), data.path("root"), 2020, 2025))
                .isInstanceOf(IbgeException.class);
        }
    }

    @Test
    void fetchesFullAnnualNationalDataAndSupportsTheProvidersGzipMetadata() throws Exception {
        server.expect(requestTo(containsString("/7533/metadados")))
            .andRespond(withSuccess(gzip(fixtureString("income-metadata")), MediaType.APPLICATION_JSON)
                .header("Content-Encoding", "gzip").header("Last-Modified", "Mon, 09 Nov 2026 10:00:00 GMT"));
        server.expect(requestTo(containsString("/7533/periodos/all/variaveis/10816?localidades=N1%5B1%5D&classificacao=1019%5B49243%5D")))
            .andRespond(withSuccess(fixtureString("income-series"), MediaType.APPLICATION_JSON));
        var result = client.series(INCOME, 2020, 2021);
        assertThat(result.batch().observations()).hasSize(2);
        assertThat(result.definition().unit()).isEqualTo("BRL_REAL_2025_MONTHLY_PER_PERSON");
        assertThat(result.batch().sourceUpdatedAt()).isNull();
        server.verify();
    }

    @Test
    void retriesTransientFailuresButRejectsPermanentAndLongRateLimitFailures() throws Exception {
        server.expect(requestTo(containsString("/7435/metadados")))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        expectGini();
        assertThat(client.series(GINI, 2024, 2025).batch().observations()).hasSize(2);
        server.verify();
        server.reset();
        server.expect(requestTo(containsString("/7435/metadados"))).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.series(GINI, 2024, 2025)).hasMessage("IBGE HTTP failure: 404");
        server.verify();
        server.reset();
        server.expect(requestTo(containsString("/7435/metadados")))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120"));
        assertThatThrownBy(() -> client.series(GINI, 2024, 2025)).hasMessageContaining("longer retry interval");
        server.verify();
    }

    @Test
    void rejectsDuplicateJsonFieldsAndOversizedResponses() {
        server.expect(requestTo(containsString("/7435/metadados")))
            .andRespond(withSuccess("{\"id\":7435,\"id\":7533}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.series(GINI, 2024, 2025)).hasMessage("Invalid IBGE JSON response");
        server.verify();
        server.reset();
        server.expect(requestTo(containsString("/7435/metadados")))
            .andRespond(withSuccess(" ".repeat(1_000_001), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.series(GINI, 2024, 2025)).hasMessageContaining("oversized");
        server.verify();
    }

    private void expectGini() throws Exception {
        server.expect(requestTo(containsString("/7435/metadados")))
            .andRespond(withSuccess(fixtureString("gini-metadata"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/7435/periodos/all/variaveis/10681")))
            .andRespond(withSuccess(fixtureString("gini-series"), MediaType.APPLICATION_JSON));
    }

    private IbgeClient.ImportedSeries decode(IbgeCatalog.Entry entry, String kind, int from, int to) throws Exception {
        return IbgeClient.decode(entry, fixture(kind + "-metadata"), mapper.readTree(fixtureString(kind + "-series")), from, to);
    }

    private ObjectNode fixture(String name) throws Exception { return (ObjectNode) mapper.readTree(fixtureString(name)); }

    private ObjectNode fixtureArray(String name) throws Exception {
        return mapper.createObjectNode().set("root", mapper.readTree(fixtureString(name)));
    }

    private static ObjectNode values(ObjectNode data) {
        return (ObjectNode) data.at("/root/0/resultados/0/series/0/serie");
    }

    private static String fixtureString(String name) throws Exception {
        try (var input = IbgeClientTest.class.getResourceAsStream("/ibge/" + name + ".json")) {
            if (input == null) throw new IllegalStateException("Missing IBGE contract fixture");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] gzip(String value) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(value.getBytes(StandardCharsets.UTF_8)); }
        return bytes.toByteArray();
    }
}
