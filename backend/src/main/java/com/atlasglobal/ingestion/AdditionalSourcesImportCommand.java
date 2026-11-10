package com.atlasglobal.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("${atlas.imf-import.run-once:false} || ${atlas.un-import.run-once:false} || ${atlas.eurostat-import.run-once:false}")
class AdditionalSourcesImportCommand implements ApplicationRunner {
    private final AnnualImportService importer;
    private final ConfigurableApplicationContext context;
    private final String source;

    AdditionalSourcesImportCommand(AnnualImportService importer, ConfigurableApplicationContext context,
                                   @Value("${atlas.imf-import.run-once:false}") boolean imf,
                                   @Value("${atlas.un-import.run-once:false}") boolean un) {
        this.importer = importer; this.context = context;
        this.source = imf ? "IMF" : un ? "UNDP" : "EUROSTAT";
    }

    @Override
    public void run(ApplicationArguments args) {
        try { importer.run(source); }
        finally { context.close(); }
    }
}
