package com.atlasglobal.integration.ibge;

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

/** Explicit national import; public API queries and application startup never request IBGE data. */
@Service
@EnableConfigurationProperties(ImportProperties.class)
public class IbgeImportService {
    private static final Logger LOG = LoggerFactory.getLogger(IbgeImportService.class);
    private static final long LOCK_KEY = 728301952149L;
    private final IbgeClient provider;
    private final ImportStore store;
    private final ImportProperties settings;
    private final DataSource database;

    IbgeImportService(IbgeClient provider, ImportStore store, ImportProperties settings, DataSource database) {
        this.provider = provider; this.store = store; this.settings = settings; this.database = database;
    }

    public UUID run() {
        if (!settings.countries().contains("BRA")) throw new IllegalArgumentException("IBGE import requires BRA in the configured country scope");
        try (Connection lock = database.getConnection()) {
            try (var statement = lock.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, LOCK_KEY);
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) throw new IllegalStateException("IBGE import already running");
                }
            }
            try {
                try (var statement = lock.prepareStatement("SELECT EXISTS (SELECT 1 FROM country WHERE iso3_code = 'BRA')");
                     var result = statement.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) throw new IllegalStateException("Import the Brazil country catalog before IBGE data");
                }
                return importData();
            } finally {
                try (var statement = lock.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    statement.setLong(1, LOCK_KEY); statement.execute();
                }
            }
        } catch (SQLException ex) { throw new IllegalStateException("IBGE import database lock unavailable", ex); }
    }

    private UUID importData() {
        UUID source = store.ensureSource(IbgeCatalog.SOURCE);
        UUID execution = store.start(source, "BRA;years=" + settings.fromYear() + ":" + settings.endYear());
        try {
            for (IbgeCatalog.Entry entry : IbgeCatalog.ENTRIES) {
                var imported = provider.series(entry, settings.fromYear(), settings.endYear());
                UUID series = store.series(source, imported.definition(), imported.metadata());
                store.observations(execution, series, imported.batch());
                LOG.info("Imported provider=IBGE indicator={} dataset={} records={} importId={}",
                    entry.code(), imported.definition().datasetCode(), imported.batch().observations().size(), execution);
            }
            store.complete(execution);
            LOG.info("Completed provider=IBGE importId={}", execution);
            return execution;
        } catch (RuntimeException ex) {
            store.fail(execution);
            LOG.error("Failed provider=IBGE importId={} reason={}", execution, ex.getMessage());
            throw ex;
        }
    }
}
