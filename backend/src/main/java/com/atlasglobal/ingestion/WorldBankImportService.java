package com.atlasglobal.ingestion;

import com.atlasglobal.integration.worldbank.WorldBankCatalog;
import com.atlasglobal.integration.worldbank.WorldBankProvider;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(ImportProperties.class)
public class WorldBankImportService {
    private static final Logger LOG = LoggerFactory.getLogger(WorldBankImportService.class);
    private static final long LOCK_KEY = 728301952147L;
    private final DataProvider provider;
    private final ImportStore store;
    private final ImportProperties settings;
    private final DataSource database;

    public WorldBankImportService(WorldBankProvider provider, ImportStore store,
                                 ImportProperties settings, DataSource database) {
        this.provider = provider; this.store = store; this.settings = settings; this.database = database;
    }

    public UUID run() {
        return run(settings.fromYear(), settings.toYear());
    }

    public UUID refreshRecent() {
        return run(Math.max(settings.fromYear(), settings.toYear() - 5),
            settings.toYear());
    }

    private UUID run(int fromYear, int toYear) {
        // A dedicated session owns the lock across short page transactions and across application instances.
        try (Connection lock = database.getConnection()) {
            try (var statement = lock.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, LOCK_KEY);
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) throw new IllegalStateException("World Bank import already running");
                }
            }
            try { return importData(fromYear, toYear); }
            finally {
                try (var statement = lock.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    statement.setLong(1, LOCK_KEY); statement.execute();
                }
            }
        } catch (SQLException ex) { throw new IllegalStateException("Import database lock unavailable", ex); }
    }

    private UUID importData(int fromYear, int toYear) {
        UUID source = store.ensureSource();
        UUID run = store.start(source, String.join(",", settings.countries()) + ";years="
            + fromYear + ":" + toYear);
        try {
            var countries = provider.countries();
            for (String requested : settings.countries()) {
                if (countries.stream().noneMatch(c -> c.iso3().equals(requested))) {
                    throw new IllegalArgumentException("Requested country unavailable: " + requested);
                }
            }
            store.countries(countries);
            for (var entry : WorldBankCatalog.ENTRIES) {
                UUID series = store.series(source, entry, provider.indicator(entry.code()));
                provider.observations(settings.countries(), entry.code(), fromYear, toYear,
                    batch -> store.observations(run, series, batch));
                LOG.info("Imported provider={} indicator={} importId={}", provider.code(), entry.code(), run);
            }
            store.complete(run);
            LOG.info("Completed provider={} importId={}", provider.code(), run);
            return run;
        } catch (RuntimeException ex) {
            store.fail(run);
            LOG.error("Failed provider={} importId={} reason={}", provider.code(), run, ex.getMessage());
            throw ex;
        }
    }
}
