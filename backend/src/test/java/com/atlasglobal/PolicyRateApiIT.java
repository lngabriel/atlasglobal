package com.atlasglobal;

import com.atlasglobal.ingestion.ImportProperties;
import com.atlasglobal.ingestion.ImportStore;
import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.policyrate.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"atlas.import.run-once=false", "atlas.import.schedule-enabled=false", "atlas.policy-rates.schedule-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class PolicyRateApiIT {
    @Autowired MockMvc mvc;
    @Autowired ImportStore imports;
    @Autowired PolicyRateStore rates;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource database;
    private UUID bcb, bis;
    private final LocalDate first = LocalDate.parse("2026-11-02");
    private final ProviderData.Source bisSource = new ProviderData.Source("BIS", "BIS", "https://stats.bis.org/", "Test official policy rates", "https://data.bis.org/topics/CBPOL");

    @BeforeEach
    void dataset() {
        imports.countries(List.of(country("BR", "BRA"), country("US", "USA"), country("FR", "FRA"), country("SG", "SGP")));
        bcb = imports.ensureSource(new ProviderData.Source("BCB", "Banco Central", "https://api.bcb.gov.br/", "Test Selic", "https://dadosabertos.bcb.gov.br/"));
        bis = imports.ensureSource(bisSource);
        UUID execution = imports.start(bcb, "policy-api-test");
        rates.persist(execution, bcb, "BRA", series("BR", "432", List.of(point(first, "13.75"), point(first.plusDays(1), "0"), point(first.plusDays(2), null))), "Target");
        imports.complete(execution);
        execution = imports.start(bis, "policy-api-test");
        rates.persist(execution, bis, "USA", series("US", "D.US", List.of(point(first, "-0.123456789"), point(first.plusDays(1), "3.875"))), "Midpoint");
        imports.complete(execution);
    }

    @Test
    void identifiesDiscontinuedArgentineRatesWithoutDiscardingPublishedHistory() throws Exception {
        imports.countries(List.of(country("AR", "ARG")));
        UUID execution = imports.start(bis, "policy-argentina-test");
        rates.persist(execution, bis, "ARG", new PolicyRateData.Series("AR", "D.AR", "Historical instrument",
            "From 10 July 2025 onwards: no policy rate adopted", "BCRA", List.of(point(LocalDate.parse("2025-07-10"), "29"))), "Historical instrument");
        imports.complete(execution);
        mvc.perform(get("/api/v1/policy-rates").param("countries", "ARG"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.countries[0].latestAvailable.date").value("2025-07-10"))
            .andExpect(jsonPath("$.countries[0].unavailableReason").value(org.hamcrest.Matchers.containsString("encerrado em 2025")));
    }

    @Test
    void exportsDatedRatesWithNativeFrequencyAndExactDecimals() throws Exception {
        String csv = new String(mvc.perform(get("/api/v1/policy-rates/export").param("countries", "BRA,USA")
                .param("from", "2026-11-02").param("to", "2026-11-05").accept("text/csv"))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andReturn().getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFcountry_code").contains("2026-11-02,-0.123456789", "2026-11-03,0,", "\"DAILY\"", "\"MISSING\"");
        assertThat(csv.split("\r\n")).hasSize(6);
    }

    @Test
    void returnsLatestDatedValuesAndAlignsOnlyActualObservations() throws Exception {
        mvc.perform(get("/api/v1/policy-rates").param("countries", "bra,USA"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unit").value("PERCENT_PER_ANNUM"))
            .andExpect(jsonPath("$.countries[0].latestAvailable.date").value("2026-11-03"))
            .andExpect(jsonPath("$.countries[0].latestAvailable.value").value(0))
            .andExpect(jsonPath("$.countries[0].series.frequency").value("DAILY"))
            .andExpect(jsonPath("$.countries[0].series.source.code").value("BCB"))
            .andExpect(jsonPath("$.countries[0].series.source.documentationUrl").value(org.hamcrest.Matchers.containsString("432-taxa-de-juros")))
            .andExpect(jsonPath("$.countries[0].latestAvailable.sourceUpdatedAt").isEmpty());
        var result = mvc.perform(get("/api/v1/policy-rates/history").param("countries", "BRA,USA")
                .param("from", "2026-11-02").param("to", "2026-11-05"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.observations.length()").value(5))
            .andExpect(jsonPath("$.observations[4].date").value("2026-11-04"))
            .andExpect(jsonPath("$.observations[4].status").value("MISSING"))
            .andReturn().getResponse().getContentAsString();
        assertThat(result).contains("-0.123456789", "3.875");
    }

    @Test
    void unavailableCoverageIsNotInventedAndInvalidQueriesAreRejected() throws Exception {
        mvc.perform(get("/api/v1/policy-rates").param("countries", "SGP"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.countries[0].series").isEmpty())
            .andExpect(jsonPath("$.countries[0].latestAvailable").isEmpty())
            .andExpect(jsonPath("$.countries[0].unavailableReason").value(org.hamcrest.Matchers.containsString("câmbio")));
        for (String codes : List.of("BRA,BRA", "BRA,", "BRA,USA,FRA,SGP,TWN,UKR")) {
            mvc.perform(get("/api/v1/policy-rates").param("countries", codes)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/policy-rates").param("countries", "ZZZ")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/policy-rates/history").param("countries", "BRA").param("from", "2026-02-30"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/policy-rates/history").param("countries", "BRA").param("to", LocalDate.now(ZoneOffset.UTC).plusDays(1).toString()))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/policy-rates/history").param("countries", "BRA").param("from", "1990-01-01").param("to", "2026-01-01"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/policy-rates")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void revisesDatesIdempotentlyWithoutChangingAnnualObservationsOrHistoricalDates() {
        long annual = jdbc.queryForObject("SELECT count(*) FROM indicator_observation", Long.class);
        UUID execution = imports.start(bcb, "policy-repeat");
        rates.persist(execution, bcb, "BRA", series("BR", "432", List.of(point(first.plusDays(1), "1.25"))), "Target");
        rates.persist(execution, bcb, "BRA", series("BR", "432", List.of(point(first.plusDays(1), "1.25"))), "Target");
        imports.complete(execution);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM policy_rate_series WHERE data_source_id=?", Long.class, bcb)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM policy_rate_observation WHERE series_id IN (SELECT id FROM policy_rate_series WHERE data_source_id=?)", Long.class, bcb)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT value FROM policy_rate_observation WHERE observation_date=? AND series_id IN (SELECT id FROM policy_rate_series WHERE data_source_id=?)",
            BigDecimal.class, java.sql.Date.valueOf(first.plusDays(1)), bcb)).isEqualByComparingTo("1.25");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM indicator_observation", Long.class)).isEqualTo(annual);
    }

    @Test
    void importsPublishedEuroAreaRatesWithoutOverwritingBrazilWithBisData() {
        var provider = new PolicyRateProvider() {
            public ProviderData.Source source() { return bisSource; }
            public long lockKey() { return 728301952151L; }
            public List<PolicyRateData.Series> load(LocalDate from, LocalDate to) {
                return List.of(series("XM", "D.XM", List.of(point(LocalDate.parse("1998-01-01"), "1"), point(LocalDate.parse("2000-01-01"), "2"))),
                    series("BR", "D.BR", List.of(point(first, "99"))));
            }
        };
        importer(List.of(provider)).run();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM policy_rate_observation o JOIN policy_rate_series s ON s.id=o.series_id JOIN country c ON c.id=s.country_id WHERE c.iso3_code='FRA' AND observation_date<'1999-01-01'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM policy_rate_series s JOIN country c ON c.id=s.country_id WHERE c.iso3_code='BRA'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT scope_note FROM policy_rate_series s JOIN country c ON c.id=s.country_id WHERE c.iso3_code='FRA'", String.class)).contains("1999");
    }

    @Test
    void recordsProviderFailureWithoutDeletingPreviouslyStoredRates() {
        var provider = new PolicyRateProvider() {
            public ProviderData.Source source() { return bisSource; }
            public long lockKey() { return 728301952151L; }
            public List<PolicyRateData.Series> load(LocalDate from, LocalDate to) { throw new IllegalStateException("Controlled outage"); }
        };
        long count = jdbc.queryForObject("SELECT count(*) FROM policy_rate_observation", Long.class);
        assertThatThrownBy(() -> importer(List.of(provider)).run()).hasMessageContaining("imports failed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM data_import WHERE data_source_id=? AND status='FAILED'", Long.class, bis)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM policy_rate_observation", Long.class)).isEqualTo(count);
    }

    private PolicyRateImportService importer(List<PolicyRateProvider> providers) {
        return new PolicyRateImportService(providers, imports, rates, new PolicyRateProperties(LocalDate.parse("1990-01-01"), LocalDate.parse("2026-11-05")),
            new ImportProperties(List.of("BRA", "USA", "FRA"), 1960, 0), jdbc, database);
    }
    private ProviderData.Country country(String iso2, String iso3) { return new ProviderData.Country(iso2, iso3, iso3, iso3, null, null, null, null, null); }
    private PolicyRateData.Point point(LocalDate date, String value) { return new PolicyRateData.Point(date, value == null ? null : new BigDecimal(value), null); }
    private PolicyRateData.Series series(String area, String code, List<PolicyRateData.Point> points) { return new PolicyRateData.Series(area, code, "Test instrument", "Test methodology", "Test central bank", points); }
}
