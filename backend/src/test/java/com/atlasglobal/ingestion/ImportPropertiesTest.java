package com.atlasglobal.ingestion;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportPropertiesTest {
    @Test
    void resolvesCurrentYearAtExecutionTimeWithoutReplacingTheSentinel() {
        var settings = new ImportProperties(List.of("BRA"), 1960, 0);
        assertThat(settings.toYear()).isZero();
        assertThat(settings.endYear()).isEqualTo(Year.now(ZoneOffset.UTC).getValue());
    }

    @Test
    void rejectsInvertedOrFutureIntervals() {
        assertThatThrownBy(() -> new ImportProperties(List.of("BRA"), 2024, 2023))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportProperties(List.of("BRA"), 1960, Year.now(ZoneOffset.UTC).getValue() + 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateOrUnsafeCountryCodes() {
        assertThatThrownBy(() -> new ImportProperties(List.of("BRA", "BRA"), 1960, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportProperties(List.of("BRA/USA"), 1960, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
