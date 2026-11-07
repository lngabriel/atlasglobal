package com.atlasglobal.ingestion;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "atlas.import.run-once", havingValue = "true")
class ImportCommand implements ApplicationRunner {
    private final WorldBankImportService importer;
    private final ConfigurableApplicationContext context;
    ImportCommand(WorldBankImportService importer, ConfigurableApplicationContext context) {
        this.importer = importer; this.context = context;
    }
    @Override
    public void run(ApplicationArguments args) {
        try { importer.run(); }
        finally { context.close(); }
    }
}
