package com.atlasglobal.ingestion;

import com.atlasglobal.integration.oecd.OecdImportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "atlas.oecd-import.run-once", havingValue = "true")
class OecdImportCommand implements ApplicationRunner {
    private final OecdImportService importer;
    private final ConfigurableApplicationContext context;

    OecdImportCommand(OecdImportService importer, ConfigurableApplicationContext context,
                      @Value("${atlas.import.run-once:false}") boolean worldBankCommand) {
        if (worldBankCommand) throw new IllegalArgumentException("Run one provider import command at a time");
        this.importer = importer;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        try { importer.run(); }
        finally { context.close(); }
    }
}
