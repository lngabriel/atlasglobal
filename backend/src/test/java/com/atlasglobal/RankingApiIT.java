package com.atlasglobal;

import com.atlasglobal.ingestion.ImportStore;
import com.atlasglobal.ingestion.ProviderData;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"atlas.import.run-once=false", "atlas.import.schedule-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class RankingApiIT {
    private static final String PATH = "/api/v1/indicators/TEST.RANKING/ranking";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ImportStore store;
    @MockitoSpyBean WorldBankProvider provider;
    private UUID source;
    private UUID indicator;
    private UUID series;
    private UUID emptySeries;
    private UUID first;
    private UUID second;
    private UUID zero;
    private UUID negative;
    private UUID missing;
    private int totalCountries;

    @BeforeEach
    void dataset() {
        first = country("R1", "RAA", "Zulu");
        second = country("R2", "RAB", "Alpha");
        zero = country("R3", "RAC", "Zero");
        negative = country("R4", "RAD", "Negative");
        missing = country("R5", "RAE", "Missing current value");
        totalCountries = jdbc.queryForObject("SELECT count(*) FROM country", Integer.class);
        source = store.ensureSource(new ProviderData.Source("TEST_RANKING_SOURCE", "Test ranking provider",
            "https://example.test/", "Controlled ranking fixture", "https://example.test/docs"));
        indicator = indicator("TEST.RANKING");
        series = series(indicator, "BASE", "TEST.RANKING", "USD_CURRENT");
        emptySeries = series(indicator("TEST.RANKING.EMPTY"), "EMPTY", "TEST.RANKING.EMPTY", "USD_CURRENT");
        observation(series, first, 2024, "10.1234567890123456789");
        observation(series, second, 2024, "10.1234567890123456789");
        observation(series, zero, 2024, "0");
        observation(series, negative, 2024, "-2.5");
        observation(series, missing, 2024, null);
        observation(series, first, 2023, "100");
        observation(series, missing, 2023, "500");
        // A newer explicit missing record cannot choose a newer reference year or become zero.
        observation(series, first, 2025, null);
        observation(series, missing, 2025, null);
    }

    @Test
    void usesOneNumericReferenceYearWithPartialCoverageAndPreservesPrecision() throws Exception {
        String body = mvc.perform(get(PATH).param("direction", "desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.year").value(2024))
            .andExpect(jsonPath("$.availableYears").value(contains(2024, 2023)))
            .andExpect(jsonPath("$.series.id").value(series.toString()))
            .andExpect(jsonPath("$.series.unit").value("USD_CURRENT"))
            .andExpect(jsonPath("$.series.source.code").value("TEST_RANKING_SOURCE"))
            .andExpect(jsonPath("$.coverage.totalCountries").value(totalCountries))
            .andExpect(jsonPath("$.coverage.withData").value(4))
            .andExpect(jsonPath("$.coverage.withoutData").value(totalCountries - 4))
            .andExpect(jsonPath("$.ranking.totalElements").value(4))
            .andExpect(jsonPath("$.ranking.content[*].country.iso3Code").value(contains("RAA", "RAB", "RAC", "RAD")))
            .andExpect(jsonPath("$.ranking.content[*].rank").value(contains(1, 1, 3, 4)))
            .andReturn().getResponse().getContentAsString();
        var parsed = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body);
        assertThat(parsed.at("/ranking/content/0/value").decimalValue()).isEqualByComparingTo("10.1234567890123456789");
        assertThat(parsed.at("/ranking/content/2/value").decimalValue()).isZero();
        assertThat(parsed.at("/ranking/content/3/value").decimalValue()).isEqualByComparingTo("-2.5");
        verifyNoInteractions(provider);
    }

    @Test
    void reversesValueOrderWithoutBreakingTiesAndKeepsGlobalRanksAcrossPages() throws Exception {
        mvc.perform(get(PATH).param("direction", "asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ranking.content[*].country.iso3Code").value(contains("RAD", "RAC", "RAA", "RAB")))
            .andExpect(jsonPath("$.ranking.content[*].rank").value(contains(1, 2, 3, 3)));
        for (int page = 0; page < 3; page++) {
            String expectedCountry = List.of("RAA", "RAB", "RAC").get(page);
            int expectedRank = List.of(1, 1, 3).get(page);
            mvc.perform(get(PATH).param("direction", "desc").param("page", String.valueOf(page)).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranking.page").value(page))
                .andExpect(jsonPath("$.ranking.totalElements").value(4))
                .andExpect(jsonPath("$.ranking.totalPages").value(4))
                .andExpect(jsonPath("$.ranking.content[0].country.iso3Code").value(expectedCountry))
                .andExpect(jsonPath("$.ranking.content[0].rank").value(expectedRank));
        }
        mvc.perform(get(PATH).param("direction", "desc").param("page", "4").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ranking.content").isEmpty())
            .andExpect(jsonPath("$.ranking.totalElements").value(4))
            .andExpect(jsonPath("$.coverage.withData").value(4));
    }

    @Test
    void honorsExplicitYearsWithoutCarryingOlderOrNewerValues() throws Exception {
        mvc.perform(get(PATH).param("year", "2023").param("direction", "desc"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.year").value(2023))
            .andExpect(jsonPath("$.coverage.withData").value(2))
            .andExpect(jsonPath("$.ranking.content[*].country.iso3Code").value(contains("RAE", "RAA")))
            .andExpect(jsonPath("$.ranking.content[0].value").value(500));
        for (String year : List.of("2022", "2025")) {
            mvc.perform(get(PATH).param("year", year))
                .andExpect(status().isOk()).andExpect(jsonPath("$.year").value(Integer.parseInt(year)))
                .andExpect(jsonPath("$.coverage.withData").value(0))
                .andExpect(jsonPath("$.coverage.withoutData").value(totalCountries))
                .andExpect(jsonPath("$.ranking.content").isEmpty());
        }
    }

    @Test
    void reportsEmptySeriesWithoutInventingAReferenceYear() throws Exception {
        mvc.perform(get("/api/v1/indicators/TEST.RANKING.EMPTY/ranking"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.year").isEmpty())
            .andExpect(jsonPath("$.availableYears").isEmpty())
            .andExpect(jsonPath("$.series.id").value(emptySeries.toString()))
            .andExpect(jsonPath("$.coverage.withData").value(0))
            .andExpect(jsonPath("$.coverage.withoutData").value(totalCountries))
            .andExpect(jsonPath("$.ranking.totalElements").value(0))
            .andExpect(jsonPath("$.ranking.totalPages").value(0))
            .andExpect(jsonPath("$.ranking.content").isEmpty());
    }

    @Test
    void requiresOneExplicitDefinitionWhenAnIndicatorHasMultipleSeries() throws Exception {
        UUID alternate = series(indicator, "REBASED", "TEST.RANKING", "USD_PPP_2025_ANNUAL");
        observation(alternate, missing, 2025, "999");
        mvc.perform(get(PATH)).andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(PATH).param("seriesId", series.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.year").value(2024))
            .andExpect(jsonPath("$.series.unit").value("USD_CURRENT"))
            .andExpect(jsonPath("$.coverage.withData").value(4));
        mvc.perform(get(PATH).param("seriesId", alternate.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.year").value(2025))
            .andExpect(jsonPath("$.series.unit").value("USD_PPP_2025_ANNUAL"))
            .andExpect(jsonPath("$.coverage.withData").value(1))
            .andExpect(jsonPath("$.ranking.content[0].value").value(999));
        mvc.perform(get(PATH).param("seriesId", emptySeries.toString())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsNationalDefinitionsRegardlessOfExplicitSeriesSelection() throws Exception {
        for (String providerCode : List.of("WORLD_BANK", "IBGE", "BCB")) {
            UUID nationalSource = store.ensureSource(new ProviderData.Source(providerCode, providerCode,
                "https://example.test/", "National definition fixture", "https://example.test/docs"));
            String code = "TEST.RANKING.NATIONAL." + providerCode.replace("_", "");
            UUID nationalSeries = store.series(nationalSource,
                new ProviderData.Series(code, "National fixture", "INEQUALITY", "PERCENT_POPULATION", "TEST"),
                new ProviderData.Indicator(providerCode.equals("WORLD_BANK") ? "SI.POV.NAHC" : "TEST.NATIONAL",
                    "National fixture", "National-only definition", providerCode));
            mvc.perform(get("/api/v1/indicators/" + code + "/ranking"))
                .andExpect(status().isConflict());
            mvc.perform(get("/api/v1/indicators/" + code + "/ranking").param("seriesId", nationalSeries.toString()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(provider);
    }

    @Test
    void validatesInputsAndPublishesAReadOnlyOpenApiContract() throws Exception {
        for (String[] invalid : List.of(new String[]{"year", "1799"}, new String[]{"year", "2201"},
                new String[]{"year", "2024.5"}, new String[]{"page", "-1"}, new String[]{"page", "10001"},
                new String[]{"size", "0"}, new String[]{"size", "101"}, new String[]{"sort", "name"},
                new String[]{"sort", "value; DROP TABLE country"}, new String[]{"direction", "DESC"},
                new String[]{"seriesId", "not-a-uuid"})) {
            mvc.perform(get(PATH).param(invalid[0], invalid[1])).andExpect(status().isBadRequest());
        }
        mvc.perform(get(PATH).param("seriesId", UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/indicators/NOT.FOUND/ranking")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/indicators/BAD_CODE/ranking")).andExpect(status().isBadRequest());
        mvc.perform(post(PATH)).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/openapi")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/indicators/{indicatorCode}/ranking'].get").exists());
        verifyNoInteractions(provider);
    }

    private UUID country(String iso2, String iso3, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO country(id,iso2_code,iso3_code,name,original_name) VALUES (?,?,?,?,?)",
            id, iso2, iso3, name, name);
        return id;
    }

    private UUID indicator(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO indicator(id,code,name,description,category) VALUES (?,?,?,?,?)",
            id, code, code, "Controlled ranking definition", "ECONOMY");
        return id;
    }

    private UUID series(UUID definition, String dataset, String externalCode, String unit) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO indicator_series(id,indicator_id,data_source_id,external_code,dataset_code,
                original_name,methodology,source_organization,unit,frequency)
            VALUES(?,?,?,?,?,'Ranking fixture','One selected definition','Test source',?,'ANNUAL')
            """, id, definition, source, externalCode, dataset, unit);
        return id;
    }

    private void observation(UUID selectedSeries, UUID country, int year, String raw) {
        BigDecimal value = raw == null ? null : new BigDecimal(raw);
        jdbc.update("""
            INSERT INTO indicator_observation(id,country_id,series_id,period,value,status,source_updated_at,imported_at)
            VALUES(?,?,?,?,?,?,DATE '2026-10-08',?)
            """, UUID.randomUUID(), country, selectedSeries, year, value,
            value == null ? "MISSING" : "AVAILABLE", Timestamp.from(Instant.now()));
    }
}
