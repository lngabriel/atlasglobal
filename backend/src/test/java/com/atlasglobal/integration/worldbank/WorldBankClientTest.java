package com.atlasglobal.integration.worldbank;

import com.atlasglobal.ingestion.ProviderData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WorldBankClientTest {
    private MockRestServiceServer server;
    private WorldBankClient client;
    private WorldBankProvider provider;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl(WorldBankConfiguration.BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WorldBankClient(builder.build(), new ObjectMapper(),
            new WorldBankProperties(500, Duration.ofSeconds(1), Duration.ofSeconds(1), 2, Duration.ZERO));
        provider = new WorldBankProvider(client);
    }

    @Test
    void excludesAggregatesAndTranslatesCountryNames() {
        server.expect(requestTo(containsString("/country?"))).andRespond(withSuccess(fixture("countries"), MediaType.APPLICATION_JSON));
        var countries = provider.countries();
        assertThat(countries).extracting(ProviderData.Country::iso3).containsExactly("BRA", "USA");
        assertThat(countries.getFirst().name()).isEqualTo("Brasil");
        assertThat(countries.getFirst().originalName()).isEqualTo("Brazil");
        server.verify();
    }

    @Test
    void preservesSourceMetadataAndOriginalMethodology() {
        server.expect(requestTo(containsString("source=2"))).andRespond(withSuccess(fixture("indicator"), MediaType.APPLICATION_JSON));
        var indicator = provider.indicator("NY.GDP.MKTP.CD");
        assertThat(indicator.originalName()).isEqualTo("GDP (current US$)");
        assertThat(indicator.methodology()).contains("current prices");
    }

    @Test
    void preservesDecimalValuesAndSourceUpdateDate() {
        server.expect(requestTo(containsString("/indicator/NY.GDP.MKTP.CD")))
            .andRespond(withSuccess(fixture("observations"), MediaType.APPLICATION_JSON));
        var batches = new ArrayList<ProviderData.Batch>();
        provider.observations(List.of("BRA", "USA", "CHN", "RUS", "DEU", "JPN", "GBR", "FRA", "IND"),
            "NY.GDP.MKTP.CD", 2022, 2025, batches::add);
        assertThat(batches.getFirst().sourceUpdatedAt()).isEqualTo("2026-10-08");
        assertThat(batches.getFirst().observations()).hasSize(36);
        assertThat(batches.getFirst().observations().getFirst().value()).isEqualByComparingTo("2279920092492.13");
    }

    @Test
    void followsAllPagesAndKeepsNullSeparateFromZero() {
        String page = """
            [{"page":%d,"pages":2,"total":2,"sourceid":"2","lastupdated":"2026-10-08"},
            [{"indicator":{"id":"SP.POP.TOTL"},"countryiso3code":"BRA","date":"2024","value":%s}]]
            """;
        server.expect(requestTo(containsString("page=1"))).andRespond(withSuccess(page.formatted(1, "null"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("page=2"))).andRespond(withSuccess(page.formatted(2, "0"), MediaType.APPLICATION_JSON));
        var result = new ArrayList<ProviderData.Batch>();
        provider.observations(List.of("BRA"), "SP.POP.TOTL", 2024, 2024, result::add);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().observations().getFirst().value()).isNull();
        assertThat(result.getLast().observations().getFirst().value()).isZero();
        server.verify();
    }

    @Test
    void retriesTransientHttpFailuresButStopsAfterTheLimit() {
        server.expect(requestTo(containsString("/country?"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(containsString("/country?"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(client::countries).isInstanceOf(WorldBankException.class).hasMessageContaining("503");
        server.verify();
    }

    @Test
    void rejectsProviderErrorsEvenWhenHttpStatusIsOk() {
        server.expect(requestTo(containsString("/country?")))
            .andRespond(withSuccess("[{\"message\":[{\"id\":\"120\",\"value\":\"Invalid value\"}]}]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::countries).isInstanceOf(WorldBankException.class).hasMessageContaining("provider error");
    }

    @Test
    void rejectsUnexpectedCountriesOrSource() {
        server.expect(requestTo(containsString("/indicator/SP.POP.TOTL")))
            .andRespond(withSuccess("""
                [{"page":1,"pages":1,"total":1,"sourceid":"3","lastupdated":"2026-10-08"},[]]
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> provider.observations(List.of("BRA"), "SP.POP.TOTL", 2024, 2024, batch -> {}))
            .isInstanceOf(WorldBankException.class).hasMessageContaining("source metadata");
    }


    @Test
    void rejectsIncompletePages() {
        server.expect(requestTo(containsString("/country?"))).andRespond(withSuccess(
            "[{\"page\":1,\"pages\":1,\"total\":1},[]]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::countries).isInstanceOf(WorldBankException.class).hasMessageContaining("Incomplete");
    }

    @Test
    void doesNotRetryPermanentHttpFailures() {
        server.expect(requestTo(containsString("/country?"))).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(client::countries).isInstanceOf(WorldBankException.class).hasMessageContaining("404");
        server.verify();
    }

    @Test
    void retriesRateLimit() {
        server.expect(requestTo(containsString("/country?")))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        server.expect(requestTo(containsString("/country?")))
            .andRespond(withSuccess(fixture("countries"), MediaType.APPLICATION_JSON));
        assertThat(client.countries()).hasSize(3);
        server.verify();
    }

    @Test
    void rejectsMalformedJsonAndUnsafeParameters() {
        server.expect(requestTo(containsString("/country?")))
            .andRespond(withSuccess("invalid-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::countries).isInstanceOf(WorldBankException.class).hasMessageContaining("decode");
        assertThatThrownBy(() -> client.indicator("../country"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private String fixture(String name) {
        try (var input = getClass().getResourceAsStream("/worldbank/" + name + ".json")) {
            if (input == null) throw new IllegalArgumentException("Missing fixture");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
