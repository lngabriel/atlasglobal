package com.atlasglobal;

import com.atlasglobal.integration.worldbank.WorldBankProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"atlas.import.run-once=false", "atlas.import.schedule-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class IndicatorObservationApiIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean WorldBankProvider provider;
    private UUID source;
    private UUID indicator;
    private UUID series;
    private UUID emptySeries;
    private UUID country;
    private UUID secondCountry;

    @BeforeEach
    void dataset() {
        source = UUID.randomUUID();
        indicator = UUID.randomUUID();
        series = UUID.randomUUID();
        emptySeries = UUID.randomUUID();
        country = country("T1", "TAA");
        secondCountry = country("T2", "TAB");
        country("T3", "TAC");
        jdbc.update("""
            INSERT INTO data_source (id, code, name, base_url, description, documentation_url)
            VALUES (?, 'TEST_QUERY_SOURCE', 'Test query source', 'https://example.test/', 'Test source', 'https://example.test/docs')
            """, source);
        jdbc.update("INSERT INTO indicator (id,code,name,description,category) VALUES (?, ?, ?, ?, ?)",
            indicator, "TEST.HISTORY", "Test history", "Test definition", "ECONOMY");
        series(series, indicator, "TEST", "TEST.HISTORY", "USD_CURRENT");
        UUID emptyIndicator = UUID.randomUUID();
        jdbc.update("INSERT INTO indicator (id,code,name,description,category) VALUES (?, ?, ?, ?, ?)",
            emptyIndicator, "TEST.EMPTY", "Test empty", "Test empty definition", "DEMOGRAPHY");
        series(emptySeries, emptyIndicator, "TEST", "TEST.EMPTY", "PERSONS");
        observation(country, 2022, "12.1234567890123456789");
        observation(country, 2023, "0");
        observation(country, 2024, null);
        observation(secondCountry, 2022, "9");
        observation(secondCountry, 2024, "5");
    }

    @Test
    void filtersIndicatorCatalogAndReturnsSourceSpecificDefinitions() throws Exception {
        mvc.perform(get("/api/v1/indicators").param("q", "test.").param("category", "ECONOMY")
                .param("source", "test_query_source").param("sort", "code"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].code").value("TEST.HISTORY"));
        mvc.perform(get("/api/v1/indicators/test.history"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.indicator.code").value("TEST.HISTORY"))
            .andExpect(jsonPath("$.series[0].unit").value("USD_CURRENT"))
            .andExpect(jsonPath("$.series[0].frequency").value("ANNUAL"))
            .andExpect(jsonPath("$.series[0].methodology").value("Test methodology"))
            .andExpect(jsonPath("$.series[0].source.code").value("TEST_QUERY_SOURCE"))
            .andExpect(jsonPath("$.series[0].source.documentationUrl").value("https://example.test/docs"));
        verifyNoInteractions(provider);
    }

    @Test
    void preservesPrecisionZeroAndExplicitMissingObservations() throws Exception {
        String body = mvc.perform(get("/api/v1/countries/taa/indicators/test.history").param("from", "2022").param("to", "2024"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.observations.totalElements").value(3))
            .andExpect(jsonPath("$.observations.content[1].value").value(0))
            .andExpect(jsonPath("$.observations.content[1].status").value("AVAILABLE"))
            .andExpect(jsonPath("$.observations.content[2].value").isEmpty())
            .andExpect(jsonPath("$.observations.content[2].status").value("MISSING"))
            .andExpect(jsonPath("$.observations.content[0].sourceUpdatedAt").value("2024-09-01"))
            .andReturn().getResponse().getContentAsString();
        var json = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body);
        assertThat(json.at("/observations/content/0/value").decimalValue()).isEqualByComparingTo("12.1234567890123456789");
        verifyNoInteractions(provider);
    }

    @Test
    void paginatesHistoryWithStableCountryTiesAndNoSyntheticYears() throws Exception {
        mvc.perform(get("/api/v1/indicators/TEST.HISTORY/history").param("countries", "TAB,TAA")
                .param("from", "2022").param("to", "2025").param("size", "2").param("page", "0"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.observations.totalElements").value(5))
            .andExpect(jsonPath("$.observations.totalPages").value(3))
            .andExpect(jsonPath("$.observations.content[0].countryCode").value("TAA"))
            .andExpect(jsonPath("$.observations.content[1].countryCode").value("TAB"));
        mvc.perform(get("/api/v1/indicators/TEST.HISTORY/history").param("countries", "TAA,TAB")
                .param("from", "2022").param("to", "2025").param("direction", "desc").param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.observations.content[0].period").value(2024))
            .andExpect(jsonPath("$.observations.content[0].countryCode").value("TAA"));
        mvc.perform(get("/api/v1/countries/TAA/indicators/TEST.HISTORY").param("from", "2022").param("to", "2024").param("page", "100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.observations.content").isEmpty())
            .andExpect(jsonPath("$.observations.totalElements").value(3));
    }

    @Test
    void distinguishesLatestNumericValueFromLatestReportedMissingValue() throws Exception {
        mvc.perform(get("/api/v1/countries/TAA/indicators").param("category", "ECONOMY"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].latestAvailable.period").value(2023))
            .andExpect(jsonPath("$.content[0].latestAvailable.value").value(0))
            .andExpect(jsonPath("$.content[0].latestReported.period").value(2024))
            .andExpect(jsonPath("$.content[0].latestReported.status").value("MISSING"));
        mvc.perform(get("/api/v1/countries/TAC/indicators").param("category", "ECONOMY"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].latestAvailable").isEmpty())
            .andExpect(jsonPath("$.content[0].latestReported").isEmpty());
        mvc.perform(get("/api/v1/countries/TAC/indicators/TEST.HISTORY"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.observations.content").isEmpty());
        mvc.perform(get("/api/v1/countries/TAA/indicators").param("source", "SOURCE_NOT_FOUND"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void requiresExplicitSeriesWhenUnitsOrDatasetsDiffer() throws Exception {
        UUID alternative = UUID.randomUUID();
        series(alternative, indicator, "TEST_PPP", "TEST.HISTORY", "USD_PPP");
        mvc.perform(get("/api/v1/countries/TAA/indicators/TEST.HISTORY"))
            .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/v1/countries/TAA/indicators/TEST.HISTORY").param("seriesId", series.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.series.unit").value("USD_CURRENT"))
            .andExpect(jsonPath("$.observations.totalElements").value(3));
        mvc.perform(get("/api/v1/countries/TAA/indicators/TEST.HISTORY").param("seriesId", alternative.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.series.unit").value("USD_PPP"))
            .andExpect(jsonPath("$.observations.content").isEmpty());
        mvc.perform(get("/api/v1/countries/TAA/indicators/TEST.HISTORY").param("seriesId", emptySeries.toString()))
            .andExpect(status().isNotFound());
        jdbc.update("""
            INSERT INTO indicator_observation (id,country_id,series_id,period,value,status,imported_at)
            VALUES (?, ?, ?, 2024, 900, 'AVAILABLE', ?)
            """, UUID.randomUUID(), country, alternative, Timestamp.from(Instant.now()));
        String summary = mvc.perform(get("/api/v1/countries/TAA/indicators").param("category", "ECONOMY"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andReturn().getResponse().getContentAsString();
        for (var row : new ObjectMapper().readTree(summary).path("content")) {
            if (row.path("series").path("id").asText().equals(series.toString())) {
                assertThat(row.path("latestAvailable").path("value").decimalValue()).isZero();
            } else {
                assertThat(row.path("latestAvailable").path("value").decimalValue()).isEqualByComparingTo("900");
                assertThat(row.path("series").path("unit").asText()).isEqualTo("USD_PPP");
            }
        }
    }

    @Test
    void validatesHistoryParametersAndMissingResources() throws Exception {
        String path = "/api/v1/indicators/TEST.HISTORY/history";
        for (String codes : List.of("", "TAA,TAA", "TAA,taa", "TAA,", "TAA,TAB,TAC,TAD,TAE,TAF", "bad-input")) {
            mvc.perform(get(path).param("countries", codes)).andExpect(status().isBadRequest());
        }
        for (String[] pair : List.of(new String[]{"from", "1799"}, new String[]{"to", "2201"},
                new String[]{"seriesId", "not-a-uuid"}, new String[]{"sort", "value"}, new String[]{"size", "101"})) {
            mvc.perform(get(path).param("countries", "TAA").param(pair[0], pair[1]))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get(path).param("countries", "TAA").param("from", "2024").param("to", "2023"))
            .andExpect(status().isBadRequest());
        mvc.perform(get(path)).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("countries", "ZZZ")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/indicators/NOT.FOUND")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/indicators").param("category", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/countries/ZZZ/indicators")).andExpect(status().isNotFound());
    }

    @Test
    void documentsOnlyReadOperationsAndKeepsOperationalRoutesPrivate() throws Exception {
        String body = mvc.perform(get("/api/openapi")).andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Atlas Global API"))
            .andExpect(jsonPath("$.paths['/api/v1/countries/{iso3Code}'].get.responses['400'].content['application/problem+json'].schema.properties.status.type").value("integer"))
            .andExpect(jsonPath("$.paths['/api/v1/countries/{iso3Code}'].get.responses['200'].content['application/json'].schema").exists())
            .andExpect(jsonPath("$.paths['/api/v1/indicators/{indicatorCode}/history'].get.responses['409']").exists())
            .andReturn().getResponse().getContentAsString();
        var document = new ObjectMapper().readTree(body);
        document.path("paths").properties().forEach(entry -> {
            assertThat(entry.getKey()).startsWith("/api/v1/");
            entry.getValue().fieldNames().forEachRemaining(method -> assertThat(method).isEqualTo("get"));
        });
        assertThat(document.path("security").isMissingNode() || document.path("security").isEmpty()).isTrue();
        mvc.perform(get("/api/docs")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/v1/indicators")).andExpect(status().isMethodNotAllowed());
        for (String path : List.of("/api/v1/imports", "/api/v1/admin", "/api/v1/jobs", "/actuator/health")) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
            mvc.perform(post(path)).andExpect(status().isNotFound());
        }
        verifyNoInteractions(provider);
    }

    private UUID country(String iso2, String iso3) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO country (id,iso2_code,iso3_code,name,original_name) VALUES (?, ?, ?, ?, ?)",
            id, iso2, iso3, "Test " + iso3, "Test " + iso3);
        return id;
    }

    private void series(UUID id, UUID parent, String dataset, String externalCode, String unit) {
        jdbc.update("""
            INSERT INTO indicator_series (id,indicator_id,data_source_id,external_code,dataset_code,
                original_name,methodology,source_organization,unit,frequency)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ANNUAL')
            """, id, parent, source, externalCode, dataset, "Test original", "Test methodology", "Test organization", unit);
    }

    private void observation(UUID place, int year, String value) {
        jdbc.update("""
            INSERT INTO indicator_observation (id,country_id,series_id,period,value,status,provider_status,source_updated_at,imported_at)
            VALUES (?, ?, ?, ?, ?, ?, '', DATE '2024-09-01', ?)
            """, UUID.randomUUID(), place, series, year, value == null ? null : new BigDecimal(value),
            value == null ? "MISSING" : "AVAILABLE", Timestamp.from(Instant.now()));
    }
}
