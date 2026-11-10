package com.atlasglobal.ingestion;

import com.atlasglobal.integration.bcb.BcbImportService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "atlas.bcb-import.run-once", havingValue = "true")
class BcbImportCommand implements ApplicationRunner {
    private final BcbImportService importer;
    private final ConfigurableApplicationContext context;

    BcbImportCommand(BcbImportService importer, ConfigurableApplicationContext context) {
        this.importer = importer;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        try { importer.run(); }
        finally { context.close(); }
    }
}
