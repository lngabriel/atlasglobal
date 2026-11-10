package com.atlasglobal.ingestion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Coordinates annual providers without exposing their response formats or adding public writes. */
@Service
@EnableConfigurationProperties(ImportProperties.class)
public class AnnualImportService {
    private static final Logger LOG = LoggerFactory.getLogger(AnnualImportService.class);
    private final List<AnnualDataProvider> providers;
    private final ImportStore store;
    private final ImportProperties settings;
    private final JdbcTemplate jdbc;
    private final DataSource database;

    public AnnualImportService(List<AnnualDataProvider> providers, ImportStore store, ImportProperties settings,
                               JdbcTemplate jdbc, DataSource database) {
        this.providers = List.copyOf(providers); this.store = store; this.settings = settings;
        this.jdbc = jdbc; this.database = database;
        if (providers.stream().map(provider -> provider.source().code()).distinct().count() != providers.size()) {
            throw new IllegalArgumentException("Duplicate annual provider code");
        }
    }

    public UUID run(String code) {
        return run(code, settings);
    }

    public UUID refreshRecent(String code) {
        return run(code, new ImportProperties(settings.countries(),
            Math.max(settings.fromYear(), settings.endYear() - 5), settings.endYear()));
    }

    private UUID run(String code, ImportProperties interval) {
        AnnualDataProvider provider = providers.stream().filter(candidate -> candidate.source().code().equals(code))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown annual provider"));
        var known = new HashSet<>(jdbc.queryForList("SELECT iso3_code FROM country", String.class));
        if (!known.containsAll(interval.countries())) throw new IllegalStateException("Import the configured country catalog before annual source data");
        try (Connection lock = database.getConnection()) {
            try (var statement = lock.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, provider.lockKey());
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) throw new IllegalStateException("Annual source import already running: " + code);
                }
            }
            try { return importData(provider, interval); }
            finally {
                try (var statement = lock.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    statement.setLong(1, provider.lockKey()); statement.execute();
                }
            }
        } catch (SQLException ex) { throw new IllegalStateException("Annual import database lock unavailable", ex); }
    }

    private UUID importData(AnnualDataProvider provider, ImportProperties interval) {
        UUID source = store.ensureSource(provider.source());
        UUID execution = store.start(source, String.join(",", interval.countries()) + ";years="
            + interval.fromYear() + ":" + interval.endYear());
        try {
            for (var imported : provider.load(interval)) {
                validate(imported, interval);
                UUID series = store.series(source, imported.definition(), imported.metadata());
                store.observations(execution, series, imported.batch());
                LOG.info("Imported provider={} indicator={} dataset={} records={} importId={}",
                    provider.source().code(), imported.definition().code(), imported.definition().datasetCode(),
                    imported.batch().observations().size(), execution);
            }
            store.complete(execution);
            LOG.info("Completed annual provider={} importId={}", provider.source().code(), execution);
            return execution;
        } catch (RuntimeException ex) {
            store.fail(execution);
            LOG.error("Failed annual provider={} importId={} reason={}", provider.source().code(), execution, ex.getMessage());
            throw ex;
        }
    }

    private void validate(ProviderData.ImportedSeries imported, ImportProperties interval) {
        var keys = new HashSet<String>();
        for (var observation : imported.batch().observations()) {
            if (!interval.countries().contains(observation.countryCode()) || observation.year() < interval.fromYear()
                || observation.year() > interval.endYear() || !observation.indicatorCode().equals(imported.definition().code())
                || !keys.add(observation.countryCode() + ":" + observation.year())
                || observation.providerStatus() != null && observation.providerStatus().length() > 40) {
                throw new IllegalStateException("Annual provider returned duplicate or out-of-scope observations");
            }
        }
    }
}
