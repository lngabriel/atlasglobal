package com.atlasglobal.export;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CsvExportServiceTest {
    @Test
    void quotesCommasQuotesLineBreaksAndUnicodeWithoutChangingOrdinaryText() {
        assertThat(CsvExportService.text("Brasil, \"país\"\r\nSão Paulo"))
            .isEqualTo("\"Brasil, \"\"país\"\"\r\nSão Paulo\"");
        assertThat(CsvExportService.text(null)).isEqualTo("\"\"");
    }

    @Test
    void prefixesFormulaLikeTextWithoutSplittingCells() {
        for (String value : new String[]{"=1+2", " +SUM(1,2)", "-formula", "@A1", "＝1+2", "\tformula", "\rformula"}) {
            assertThat(CsvExportService.text(value)).startsWith("\"\t").endsWith("\"");
        }
    }
}
