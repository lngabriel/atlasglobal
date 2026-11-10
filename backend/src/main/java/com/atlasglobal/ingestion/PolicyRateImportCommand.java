package com.atlasglobal.ingestion;

import com.atlasglobal.policyrate.PolicyRateImportService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "atlas.policy-rates.run-once", havingValue = "true")
class PolicyRateImportCommand implements ApplicationRunner {
    private final PolicyRateImportService importer;
    private final ConfigurableApplicationContext context;
    PolicyRateImportCommand(PolicyRateImportService importer, ConfigurableApplicationContext context) { this.importer = importer; this.context = context; }
    public void run(ApplicationArguments args) { try { importer.run(); } finally { context.close(); } }
}
