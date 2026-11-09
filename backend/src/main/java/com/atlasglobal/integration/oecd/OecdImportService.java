package com.atlasglobal.integration.oecd;

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

/** Internal annual import; normal API queries never contact OECD. */
@Service
@EnableConfigurationProperties(ImportProperties.class)
public class OecdImportService {
    private static final Logger LOG = LoggerFactory.getLogger(OecdImportService.class);
    private static final long LOCK_KEY = 728301952148L;
    private final OecdClient provider;
    private final ImportStore store;
    private final ImportProperties settings;
    private final DataSource database;

    OecdImportService(OecdClient provider, ImportStore store, ImportProperties settings, DataSource database) {
        this.provider = provider; this.store = store; this.settings = settings; this.database = database;
    }

    public UUID run() {
        try (Connection lock = database.getConnection()) {
            try (var statement = lock.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, LOCK_KEY);
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) throw new IllegalStateException("OECD import already running");
                }
            }
            try { return importData(); }
            finally {
                try (var statement = lock.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    statement.setLong(1, LOCK_KEY); statement.execute();
                }
            }
        } catch (SQLException ex) { throw new IllegalStateException("OECD import database lock unavailable", ex); }
    }

    private UUID importData() {
        UUID source = store.ensureSource(OecdCatalog.SOURCE);
        UUID execution = store.start(source, String.join(",", settings.countries()) + ";years="
            + settings.fromYear() + ":" + settings.endYear());
        try {
            for (OecdCatalog.Entry entry : OecdCatalog.ENTRIES) {
                for (var imported : provider.series(entry, settings.countries(), settings.fromYear(), settings.endYear())) {
                    UUID series = store.series(source, imported.definition(), imported.metadata());
                    store.observations(execution, series, imported.batch());
                    LOG.info("Imported provider=OECD indicator={} dataset={} records={} importId={}",
                        entry.code(), imported.definition().datasetCode(), imported.batch().observations().size(), execution);
                }
            }
            store.complete(execution);
            LOG.info("Completed provider=OECD importId={}", execution);
            return execution;
        } catch (RuntimeException ex) {
            store.fail(execution);
            LOG.error("Failed provider=OECD importId={} reason={}", execution, ex.getMessage());
            throw ex;
        }
    }
}
