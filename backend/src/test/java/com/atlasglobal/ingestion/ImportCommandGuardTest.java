package com.atlasglobal.ingestion;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class ImportCommandGuardTest {
    private static final List<String> COMMANDS = List.of("atlas.import.run-once", "atlas.oecd-import.run-once",
        "atlas.ibge-import.run-once", "atlas.bcb-import.run-once", "atlas.policy-rates.run-once");
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(ImportCommandGuard.class);

    @Test
    void allowsNormalStartupAndOneProviderCommand() {
        context.run(application -> assertThat(application).hasNotFailed());
        for (String command : COMMANDS) {
            context.withPropertyValues(command + "=true")
                .run(application -> assertThat(application).hasNotFailed());
        }
    }

    @Test
    void rejectsEveryPairOfProviderCommandsDuringContextInitialization() {
        for (int first = 0; first < COMMANDS.size(); first++) {
            for (int second = first + 1; second < COMMANDS.size(); second++) {
                context.withPropertyValues(COMMANDS.get(first) + "=true", COMMANDS.get(second) + "=true")
                    .run(application -> assertThat(application).hasFailed()
                        .getFailure().hasRootCauseMessage("Run one provider import command at a time"));
            }
        }
    }
}
