package com.atlasglobal.ingestion;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "atlas.additional-import.schedule-enabled", havingValue = "true")
@ConditionalOnExpression("!${atlas.import.run-once:false} && !${atlas.oecd-import.run-once:false} && !${atlas.ibge-import.run-once:false} && !${atlas.bcb-import.run-once:false} && !${atlas.policy-rates.run-once:false} && !${atlas.imf-import.run-once:false} && !${atlas.un-import.run-once:false} && !${atlas.eurostat-import.run-once:false}")
class AdditionalSourcesSchedule {
    private static final Logger LOG = LoggerFactory.getLogger(AdditionalSourcesSchedule.class);
    private final AnnualImportService importer;
    private final List<AnnualDataProvider> providers;

    AdditionalSourcesSchedule(AnnualImportService importer, List<AnnualDataProvider> providers) {
        this.importer = importer; this.providers = List.copyOf(providers);
    }

    @Scheduled(cron = "${atlas.additional-import.cron:0 0 5 * * SAT}", zone = "UTC")
    void refresh() {
        for (var provider : providers) {
            try { importer.refreshRecent(provider.source().code()); }
            catch (RuntimeException ex) {
                LOG.error("Scheduled annual provider={} failed: {}", provider.source().code(), ex.getMessage());
            }
        }
    }
}
