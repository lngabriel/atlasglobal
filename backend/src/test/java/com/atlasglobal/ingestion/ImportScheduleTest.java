package com.atlasglobal.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ImportScheduleTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(ImportSchedule.class)
        .withBean(WorldBankImportService.class, () -> mock(WorldBankImportService.class))
        .withPropertyValues("atlas.import.cron=-");

    @Test
    void onlyEnablesSchedulingForAnExplicitlyEnabledLongRunningProcess() {
        context.run(application -> assertThat(application).doesNotHaveBean(ImportSchedule.class));
        context.withPropertyValues("atlas.import.schedule-enabled=true")
            .run(application -> assertThat(application).hasSingleBean(ImportSchedule.class));
        context.withPropertyValues("atlas.import.schedule-enabled=true", "atlas.import.run-once=true")
            .run(application -> assertThat(application).doesNotHaveBean(ImportSchedule.class));
    }
}
