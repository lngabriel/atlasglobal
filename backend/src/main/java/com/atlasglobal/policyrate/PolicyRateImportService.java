package com.atlasglobal.policyrate;

import com.atlasglobal.ingestion.ImportProperties;
import com.atlasglobal.ingestion.ImportStore;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties({PolicyRateProperties.class, ImportProperties.class})
public class PolicyRateImportService {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyRateImportService.class);
    // These countries entered monetary union in 1999; use the published euro-area instrument from that date only.
    private static final Set<String> EURO_1999 = Set.of("FRA", "ITA", "PRT", "ESP", "DEU", "AUT", "BEL", "FIN", "IRL", "LUX", "NLD");
    private final List<PolicyRateProvider> providers;
    private final ImportStore imports;
    private final PolicyRateStore store;
    private final PolicyRateProperties settings;
    private final ImportProperties scope;
    private final JdbcTemplate jdbc;
    private final DataSource database;

    public PolicyRateImportService(List<PolicyRateProvider> providers, ImportStore imports, PolicyRateStore store,
                                  PolicyRateProperties settings, ImportProperties scope, JdbcTemplate jdbc, DataSource database) {
        this.providers = providers; this.imports = imports; this.store = store; this.settings = settings;
        this.scope = scope; this.jdbc = jdbc; this.database = database;
    }

    public void run() { execute(settings.from()); }
    public void refresh() { execute(settings.from().isAfter(settings.end().minusDays(45)) ? settings.from() : settings.end().minusDays(45)); }

    private void execute(LocalDate from) {
        var countries = jdbc.query("SELECT iso3_code,iso2_code FROM country", (rs, row) -> Map.entry(rs.getString(1), rs.getString(2)))
            .stream().filter(entry -> scope.countries().contains(entry.getKey())).toList();
        if (countries.size() != scope.countries().size()) throw new IllegalStateException("Import the configured country catalog before policy rates");
        int failures = 0;
        for (PolicyRateProvider provider : providers) {
            if (!settings.providers().contains(provider.source().code())) continue;
            if (provider.source().code().equals("BCB") && !scope.countries().contains("BRA")) continue;
            if (provider.source().code().equals("NBU") && !scope.countries().contains("UKR")) continue;
            try { importProvider(provider, from, countries); }
            catch (RuntimeException ex) { failures++; LOG.error("Policy-rate provider={} failed: {}", provider.source().code(), ex.getMessage()); }
        }
        if (failures > 0) throw new IllegalStateException("One or more policy-rate imports failed; inspect operational logs");
    }

    private void importProvider(PolicyRateProvider provider, LocalDate from, List<Map.Entry<String, String>> countries) {
        try (Connection connection = database.getConnection()) {
            try (var acquire = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                acquire.setLong(1, provider.lockKey());
                try (var result = acquire.executeQuery()) {
                    result.next(); if (!result.getBoolean(1)) throw new IllegalStateException("Source import already running");
                }
            }
            try {
                UUID source = imports.ensureSource(provider.source());
                UUID execution = imports.start(source, "policy-rates;countries=" + String.join(",", scope.countries()) + ";dates=" + from + ":" + settings.end());
                try {
                    for (var series : provider.load(from, settings.end())) {
                        for (var country : countries) {
                            if (!matches(provider.source().code(), country.getKey(), country.getValue(), series.referenceArea())) continue;
                            var points = series.points().stream().filter(point -> !point.date().isBefore(from) && !point.date().isAfter(settings.end()))
                                .filter(point -> !series.referenceArea().equals("XM") || !point.date().isBefore(LocalDate.of(1999, 1, 1))).toList();
                            if (points.isEmpty()) continue;
                            String note = series.referenceArea().equals("XM")
                                ? "Instrumento comum da área do euro, publicado pelo BIS; aplicado a este país durante sua participação na união monetária, a partir de 1999."
                                : "Instrumento nacional identificado na metodologia original. A referência diária não significa uma nova decisão de política em cada dia.";
                            store.persist(execution, source, country.getKey(), new PolicyRateData.Series(series.referenceArea(), series.externalCode(),
                                series.instrument(), series.methodology(), series.sourceOrganization(), points), note);
                        }
                    }
                    imports.complete(execution);
                    LOG.info("Completed policy-rate provider={} importId={}", provider.source().code(), execution);
                } catch (RuntimeException ex) { imports.fail(execution); throw ex; }
            } finally {
                try (var release = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) { release.setLong(1, provider.lockKey()); release.execute(); }
            }
        } catch (SQLException ex) { throw new IllegalStateException("Policy-rate database lock unavailable", ex); }
    }

    private boolean matches(String source, String country, String iso2, String area) {
        if (country.equals("BRA")) return source.equals("BCB") && area.equals("BR");
        if (country.equals("UKR")) return source.equals("NBU") && area.equals("UA");
        if (!source.equals("BIS")) return false;
        return EURO_1999.contains(country) ? area.equals("XM") : iso2.equals(area);
    }
}
