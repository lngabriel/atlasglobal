package com.atlasglobal.policyrate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "atlas.policy-rates.schedule-enabled", havingValue = "true")
@ConditionalOnExpression("!${atlas.import.run-once:false} && !${atlas.oecd-import.run-once:false} && !${atlas.ibge-import.run-once:false} && !${atlas.bcb-import.run-once:false} && !${atlas.policy-rates.run-once:false} && !${atlas.imf-import.run-once:false} && !${atlas.un-import.run-once:false} && !${atlas.eurostat-import.run-once:false}")
class PolicyRateSchedule {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyRateSchedule.class);
    private final PolicyRateImportService service;
    PolicyRateSchedule(PolicyRateImportService service) { this.service = service; }
    @Scheduled(cron = "${atlas.policy-rates.cron:0 0 4 * * *}", zone = "UTC")
    void refresh() { try { service.refresh(); } catch (RuntimeException ex) { LOG.error("Policy-rate refresh failed: {}", ex.getMessage()); } }
}
