package com.atlasglobal.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdditionalSourcesScheduleTest {
    @Test
    void onlySchedulesInAnExplicitlyEnabledLongRunningProcess() {
        var context = new ApplicationContextRunner().withUserConfiguration(AdditionalSourcesSchedule.class)
            .withBean(AnnualImportService.class, () -> mock(AnnualImportService.class))
            .withPropertyValues("atlas.additional-import.cron=-");
        context.run(application -> assertThat(application).doesNotHaveBean(AdditionalSourcesSchedule.class));
        var enabled = context.withPropertyValues("atlas.additional-import.schedule-enabled=true");
        enabled.run(application -> assertThat(application).hasSingleBean(AdditionalSourcesSchedule.class));
        for (String flag : new String[]{"atlas.import.run-once", "atlas.oecd-import.run-once", "atlas.ibge-import.run-once",
                "atlas.bcb-import.run-once", "atlas.policy-rates.run-once", "atlas.imf-import.run-once", "atlas.un-import.run-once", "atlas.eurostat-import.run-once"}) {
            enabled.withPropertyValues(flag + "=true").run(application -> assertThat(application).doesNotHaveBean(AdditionalSourcesSchedule.class));
        }
    }
}
