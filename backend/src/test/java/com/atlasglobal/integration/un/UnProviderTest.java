package com.atlasglobal.integration.un;

import com.atlasglobal.ingestion.ImportProperties;
import com.atlasglobal.ingestion.ProviderData;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UnProviderTest {
    private String fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/un/hdr25-indices.csv")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private List<ProviderData.ImportedSeries> decode(String csv, String... countries) {
        return UnProvider.decode(new UnClient.Snapshot(2025, csv), new ImportProperties(List.of(countries), 2022, 2023));
    }

    @Test
    void importsOfficialIndicesWithTheirNativeScaleEditionAndReferenceYears() throws Exception {
        var series = decode(fixture(), "BRA", "USA", "HKG");
        assertThat(series).hasSize(2);
        var hdi = series.getFirst();
        assertThat(hdi.definition().code()).isEqualTo("UNDP.HDI");
        assertThat(hdi.definition().unit()).isEqualTo("INDEX_0_1");
        assertThat(hdi.definition().datasetCode()).isEqualTo("HDR:2025");
        assertThat(hdi.batch().sourceUpdatedAt()).isNull();
        var brazil = hdi.batch().observations().stream().filter(row -> row.countryCode().equals("BRA") && row.year() == 2023).findFirst().orElseThrow();
        assertThat(brazil.value()).isEqualByComparingTo("0.786");
        assertThat(brazil.providerStatus()).isNull();
        assertThat(brazil.indicatorCode()).isEqualTo("UNDP.HDI");
        assertThat(series.getLast().batch().observations().stream().filter(row -> row.countryCode().equals("BRA") && row.year() == 2023).findFirst().orElseThrow().value())
            .isEqualByComparingTo("0.594");
        assertThat(hdi.metadata().methodology()).contains("2025", "2023", "source estimates");
        assertThat(hdi.batch().observations()).allMatch(row -> row.year() >= 2022 && row.year() <= 2023);
    }

    @Test
    void preservesPublishedMissingCellsWithoutInventingTaiwanOrAggregateObservations() throws Exception {
        var series = decode(fixture(), "PRK", "TWN", "BRA");
        for (var item : series) {
            assertThat(item.batch().observations()).allMatch(row -> List.of("PRK", "BRA").contains(row.countryCode()));
            assertThat(item.batch().observations().stream().filter(row -> row.countryCode().equals("PRK")))
                .allMatch(row -> row.value() == null);
        }
        assertThat(decode(fixture(), "TWN")).allMatch(item -> item.batch().observations().isEmpty());
    }

    @Test
    void retainsNumericZeroAndRejectsOutOfRangeOrMalformedIndexCells() throws Exception {
        String body = fixture();
        assertThat(decode(body.replace("0.786", "0"), "BRA").getFirst().batch().observations())
            .anyMatch(row -> row.year() == 2023 && row.value().signum() == 0);
        for (String invalid : List.of("1.001", "-0.001", "NaN", "..")) {
            assertThatThrownBy(() -> decode(body.replace("0.786", invalid), "BRA"))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectsDuplicateCountryRecordsAndChangedRequiredHeaders() throws Exception {
        String body = fixture();
        String brazil = body.lines().filter(line -> line.startsWith("BRA,")).findFirst().orElseThrow();
        assertThatThrownBy(() -> decode(body + brazil + "\r\n", "BRA")).hasMessageContaining("country identity");
        assertThatThrownBy(() -> decode(body.replace("iso3,", "country_code,"), "BRA")).hasMessageContaining("metadata columns");
        assertThatThrownBy(() -> decode(body.replaceAll("hdi_([0-9]{4})", "unexpected_$1"), "BRA"))
            .hasMessageContaining("annual columns");
    }
}
