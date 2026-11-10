package com.atlasglobal.policyrate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PolicyRateScheduleTest {
    @Test
    void schedulesOnlyWhenEnabledAndNeverInAnyOneShotProcess() {
        var context = new ApplicationContextRunner().withUserConfiguration(PolicyRateSchedule.class)
            .withBean(PolicyRateImportService.class, () -> mock(PolicyRateImportService.class))
            .withPropertyValues("atlas.policy-rates.cron=-");
        context.run(application -> assertThat(application).doesNotHaveBean(PolicyRateSchedule.class));
        var enabled = context.withPropertyValues("atlas.policy-rates.schedule-enabled=true");
        enabled.run(application -> assertThat(application).hasSingleBean(PolicyRateSchedule.class));
        for (String flag : new String[]{"atlas.import.run-once", "atlas.oecd-import.run-once", "atlas.ibge-import.run-once", "atlas.bcb-import.run-once", "atlas.policy-rates.run-once"}) {
            enabled.withPropertyValues(flag + "=true").run(application -> assertThat(application).doesNotHaveBean(PolicyRateSchedule.class));
        }
    }
}
