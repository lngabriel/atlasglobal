package com.atlasglobal;

import com.atlasglobal.ingestion.ImportStore;
import com.atlasglobal.ingestion.OverviewImportStore;
import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.integration.worldbank.WorldBankCatalog;
import com.atlasglobal.integration.worldbank.WorldBankProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"atlas.import.run-once=false", "atlas.import.schedule-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class OverviewApiIT {
    @Autowired ImportStore countryStore;
    @Autowired OverviewImportStore worlds;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoSpyBean WorldBankProvider provider;
    private UUID series;
    private UUID run;

    @BeforeEach
    void series() {
        UUID source = countryStore.ensureSource();
        run = countryStore.start(source, "world-overview-integration-test");
        var entry = WorldBankCatalog.ENTRIES.getFirst();
        series = countryStore.series(source, entry,
            new ProviderData.Indicator(entry.code(), "GDP (current US$)", "Test global GDP definition", "Test source"));
    }

    @Test
    void keepsWorldValuesSeparateAndPreservesPrecisionZeroAndMissingYears() throws Exception {
        worlds.observations(run, series, batch("WLD", 2023, "123456789012345.123456789"));
        worlds.observations(run, series, batch("WLD", 2024, "0"));
        worlds.observations(run, series, batch("WLD", 2025, null));
        worlds.observations(run, series, batch("WLD", 2025, null));

        countryStore.countries(List.of(new ProviderData.Country("ZZ", "ZZZ", "Test country", "Test country",
            null, null, null, null, null)));
        countryStore.observations(run, series, batch("ZZZ", 2026, "999999999"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM world_indicator_observation WHERE series_id = ?",
            Integer.class, series)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT value FROM world_indicator_observation WHERE series_id = ? AND period = 2023",
            BigDecimal.class, series)).isEqualByComparingTo("123456789012345.123456789");
        assertThat(jdbc.queryForObject("SELECT records_imported FROM data_import WHERE id = ?", Long.class, run)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM country WHERE iso3_code = 'WLD'", Integer.class)).isZero();

        mvc.perform(get("/api/v1/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope.code").value("WLD"))
            .andExpect(jsonPath("$.scope.name").value("Mundo"))
            .andExpect(jsonPath("$.indicators[?(@.indicator.code == 'NY.GDP.MKTP.CD')].series.unit").value(contains("USD_CURRENT")))
            .andExpect(jsonPath("$.indicators[?(@.indicator.code == 'NY.GDP.MKTP.CD')].latestAvailable.period").value(contains(2024)))
            .andExpect(jsonPath("$.indicators[?(@.indicator.code == 'NY.GDP.MKTP.CD')].latestAvailable.value").value(contains(0)))
            .andExpect(jsonPath("$.indicators[?(@.indicator.code == 'NY.GDP.MKTP.CD')].latestReported.period").value(contains(2025)))
            .andExpect(jsonPath("$.indicators[?(@.indicator.code == 'NY.GDP.MKTP.CD')].latestReported.status").value(contains("MISSING")))
            .andExpect(jsonPath("$.indicators[?(@.indicator.code == 'NY.GDP.MKTP.CD')].latestAvailable.sourceUpdatedAt").value(contains("2026-10-08")));
        mvc.perform(get("/api/v1/countries/WLD")).andExpect(status().isNotFound());
        verifyNoInteractions(provider);
    }

    @Test
    void exposesMissingCoverageWithoutSynthesizingAWorldValueOrCallingProviders() throws Exception {
        mvc.perform(get("/api/v1/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indicators[?(@.indicator.code == 'NY.GDP.MKTP.CD')].latestAvailable").value(contains((Object) null)))
            .andExpect(jsonPath("$.indicators[?(@.indicator.code == 'NY.GDP.MKTP.CD')].latestReported").value(contains((Object) null)));
        mvc.perform(post("/api/v1/overview")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(provider);
    }

    @Test
    void rejectsAWholeBatchContainingCountryRecordsBeforeWritingAnyWorldValues() {
        var batch = new ProviderData.Batch(List.of(
            new ProviderData.Observation("WLD", "NY.GDP.MKTP.CD", 2023, BigDecimal.ONE, ""),
            new ProviderData.Observation("BRA", "NY.GDP.MKTP.CD", 2024, BigDecimal.TEN, "")), LocalDate.of(2026, 10, 8));
        assertThatThrownBy(() -> worlds.observations(run, series, batch))
            .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class).hasMessageContaining("WLD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM world_indicator_observation WHERE series_id = ?",
            Integer.class, series)).isZero();
        assertThat(jdbc.queryForObject("SELECT records_imported FROM data_import WHERE id = ?", Long.class, run)).isZero();
    }

    private ProviderData.Batch batch(String country, int year, String value) {
        return new ProviderData.Batch(List.of(new ProviderData.Observation(country, "NY.GDP.MKTP.CD", year,
            value == null ? null : new BigDecimal(value), "")), LocalDate.of(2026, 10, 8));
    }
}
