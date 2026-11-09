package com.atlasglobal.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "atlas.import.schedule-enabled", havingValue = "true")
@ConditionalOnExpression("!${atlas.import.run-once:false}")
class ImportSchedule {
    private static final Logger LOG = LoggerFactory.getLogger(ImportSchedule.class);
    private final WorldBankImportService importer;
    ImportSchedule(WorldBankImportService importer) { this.importer = importer; }

    @Scheduled(cron = "${atlas.import.cron:0 0 3 * * SUN}", zone = "UTC")
    void refresh() {
        try { importer.refreshRecent(); }
        catch (RuntimeException ex) { LOG.error("Scheduled World Bank import failed: {}", ex.getMessage()); }
    }
}
