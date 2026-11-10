package com.atlasglobal.integration.bcb;

import com.atlasglobal.ingestion.ImportProperties;
import com.atlasglobal.ingestion.ImportStore;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Internal Brazilian annual December snapshots; public queries never contact BCB. */
@Service
@EnableConfigurationProperties(ImportProperties.class)
public class BcbImportService {
    private static final Logger LOG = LoggerFactory.getLogger(BcbImportService.class);
    private static final long LOCK_KEY = 728301952150L;
    private final BcbClient provider;
    private final ImportStore store;
    private final ImportProperties settings;
    private final DataSource database;

    BcbImportService(BcbClient provider, ImportStore store, ImportProperties settings, DataSource database) {
        this.provider = provider; this.store = store; this.settings = settings; this.database = database;
    }

    public UUID run() {
        if (!settings.countries().contains("BRA")) throw new IllegalArgumentException("BCB import requires BRA in its country scope");
        try (Connection lock = database.getConnection()) {
            try (var statement = lock.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, LOCK_KEY);
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) throw new IllegalStateException("BCB import already running");
                }
            }
            try {
                try (var statement = lock.prepareStatement("SELECT EXISTS (SELECT 1 FROM country WHERE iso3_code = 'BRA')");
                     var result = statement.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) throw new IllegalStateException("Import the Brazil country catalog before BCB data");
                }
                return importData();
            } finally {
                try (var statement = lock.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    statement.setLong(1, LOCK_KEY); statement.execute();
                }
            }
        } catch (SQLException ex) { throw new IllegalStateException("BCB import database lock unavailable", ex); }
    }

    private UUID importData() {
        UUID source = store.ensureSource(BcbCatalog.SOURCE);
        UUID execution = store.start(source, "BRA;years=" + settings.fromYear() + ":" + settings.endYear() + ";month=12;completed-years-only");
        try {
            var observations = provider.series(settings.fromYear(), settings.endYear());
            UUID series = store.series(source, BcbCatalog.DEFINITION, BcbCatalog.METADATA);
            store.observations(execution, series, observations);
            store.complete(execution);
            LOG.info("Completed provider=BCB indicator={} records={} importId={}",
                BcbCatalog.DEFINITION.code(), observations.observations().size(), execution);
            return execution;
        } catch (RuntimeException ex) {
            store.fail(execution);
            LOG.error("Failed provider=BCB importId={} reason={}", execution, ex.getMessage());
            throw ex;
        }
    }
}
