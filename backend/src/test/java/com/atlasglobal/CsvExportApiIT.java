package com.atlasglobal;

import com.atlasglobal.ingestion.ImportStore;
import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.integration.worldbank.WorldBankProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"atlas.import.run-once=false", "atlas.import.schedule-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class CsvExportApiIT {
    @Autowired MockMvc mvc;
    @Autowired ImportStore store;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean WorldBankProvider provider;
    private UUID source;
    private UUID series;
    private ProviderData.Indicator metadata;

    @BeforeEach
    void dataset() {
        store.countries(List.of(
            new ProviderData.Country("Y1", "VAA", "País A", "Country A", null, null, null, null, null),
            new ProviderData.Country("Y2", "VAB", "País B", "Country B", null, null, null, null, null)));
        source = store.ensureSource(new ProviderData.Source("CSV_TEST", "Fonte de teste", "https://example.test/",
            "Fixture source", "https://example.test/docs"));
        metadata = new ProviderData.Indicator("CSV.TEST", "Test series", "CSV integration fixture", "Fixture");
        series = store.series(source, new ProviderData.Series("CSV.TEST", "Indicador de teste", "ECONOMY",
            "USD_CURRENT", "TEST"), metadata);
        UUID execution = store.start(source, "csv-test");
        var rows = new ArrayList<ProviderData.Observation>();
        for (int year = 1900; year <= 2020; year++) {
            rows.add(new ProviderData.Observation("VAA", "CSV.TEST", year,
                year == 1900 ? BigDecimal.ZERO : new BigDecimal("12.1234567890123456789"), null));
        }
        rows.add(new ProviderData.Observation("VAA", "CSV.TEST", 2021, null, "M"));
        rows.add(new ProviderData.Observation("VAB", "CSV.TEST", 1900, new BigDecimal("-4.50"), null));
        store.observations(execution, series, new ProviderData.Batch(rows, null));
        store.complete(execution);
    }

    @Test
    void exportsAllPagesWithPrecisionZeroMissingAndProvenance() throws Exception {
        var result = mvc.perform(get("/api/v1/exports/indicators").param("indicator", "CSV.TEST")
                .param("countries", "vaa,VAB").param("from", "1900").param("to", "2022").accept("text/csv"))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"atlas-global-CSV.TEST.csv\""))
            .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFcountry_code,country,indicator_code,indicator,period,value,");
        assertThat(csv.split("\r\n")).hasSize(124);
        assertThat(csv).contains("1900,0,\"USD_CURRENT\"", "1900,-4.50,\"USD_CURRENT\"",
            "12.1234567890123456789", "2021,,\"USD_CURRENT\"", "\"MISSING\",\"M\"",
            "\"CSV_TEST\",\"Fonte de teste\",\"" + series + "\",\"TEST\"");
        assertThat(csv).doesNotContain("2022,");
        verifyNoInteractions(provider);
    }

    @Test
    void escapesTextAndDoesNotInterpretFormulaLikeNames() throws Exception {
        jdbc.update("UPDATE country SET name=? WHERE iso3_code='VAA'", "=1+2, \"país\"\r\nlinha");
        String csv = new String(mvc.perform(get("/api/v1/exports/indicators").param("indicator", "CSV.TEST")
                .param("countries", "VAA").param("from", "1900").param("to", "1900"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("\"\t=1+2, \"\"país\"\"\r\nlinha\"");
    }

    @Test
    void validatesSelectionAndReturnsErrorsBeforeSendingAnAttachment() throws Exception {
        for (String countries : List.of("VAA,VAA", "VAA,", "VAA,VAB,VAC,VAD,VAE,VAF")) {
            mvc.perform(get("/api/v1/exports/indicators").param("indicator", "CSV.TEST")
                    .param("countries", countries).accept("text/csv"))
                .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Content-Disposition"));
        }
        mvc.perform(get("/api/v1/exports/indicators").param("indicator", "CSV.TEST").param("countries", "VZZ"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/exports/indicators").param("indicator", "CSV.TEST").param("countries", "VAA")
                .param("from", "2024").param("to", "2023"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/exports/indicators").param("indicator", "CSV.TEST").param("countries", "VAA")
                .param("from", "1799"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAnExplicitSeriesAndKeepsNationalComparisonRestrictions() throws Exception {
        store.series(source, new ProviderData.Series("CSV.TEST", "Indicador de teste", "ECONOMY",
            "PERSONS", "ALT"), metadata);
        mvc.perform(get("/api/v1/exports/indicators").param("indicator", "CSV.TEST").param("countries", "VAA")
                .accept("text/csv"))
            .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().doesNotExist("Content-Disposition"));
        mvc.perform(get("/api/v1/exports/indicators").param("indicator", "CSV.TEST").param("countries", "VAA")
                .param("seriesId", series.toString()))
            .andExpect(status().isOk());
        UUID nationalSource = store.ensureSource(new ProviderData.Source("IBGE", "IBGE", "https://example.test/",
            "National test source", "https://example.test/docs"));
        store.series(nationalSource, new ProviderData.Series("CSV.NATIONAL", "National test", "INEQUALITY",
            "GINI_COEFFICIENT", "TEST"), new ProviderData.Indicator("CSV.NATIONAL", "National", "National", "IBGE"));
        mvc.perform(get("/api/v1/exports/indicators").param("indicator", "CSV.NATIONAL").param("countries", "VAA,VAB")
                .accept("text/csv"))
            .andExpect(status().isConflict()).andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test
    void emptyIntervalsExportOnlyTheHeaderWithoutInventingRecords() throws Exception {
        String csv = new String(mvc.perform(get("/api/v1/exports/indicators").param("indicator", "CSV.TEST")
                .param("countries", "VAA").param("from", "2050").param("to", "2051"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv.split("\r\n")).hasSize(1);
    }
}
