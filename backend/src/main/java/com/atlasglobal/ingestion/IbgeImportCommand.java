package com.atlasglobal.ingestion;

import com.atlasglobal.integration.ibge.IbgeImportService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "atlas.ibge-import.run-once", havingValue = "true")
class IbgeImportCommand implements ApplicationRunner {
    private final IbgeImportService importer;
    private final ConfigurableApplicationContext context;

    IbgeImportCommand(IbgeImportService importer, ConfigurableApplicationContext context) {
        this.importer = importer;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        try { importer.run(); }
        finally { context.close(); }
    }
}
