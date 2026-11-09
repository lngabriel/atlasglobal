package com.atlasglobal.integration.oecd;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OecdCsvTest {
    @Test
    void parsesBomCrLfEscapedQuotesAndQuotedNewlines() {
        var rows = OecdCsv.records("\ufeffCODE,NOTE,VALUE\r\nUSA,\"Quoted, with \"\"quotes\"\"\r\nand newline\",123.45\r\n");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("CODE", "USA").containsEntry("VALUE", "123.45")
            .containsEntry("NOTE", "Quoted, with \"quotes\"\r\nand newline");
    }

    @Test
    void preservesEmptyFinalFieldsAndRejectsMalformedOrDuplicateColumns() {
        assertThat(OecdCsv.records("A,B\n1,").getFirst()).containsEntry("B", "");
        for (String malformed : new String[] {"A,A\n1,2", "A,B\n1", "A,B\n\"unclosed,2", "A,B\n\"closed\"junk,2", "A,B\nquote\"inside,2"}) {
            assertThatThrownBy(() -> OecdCsv.records(malformed)).isInstanceOf(OecdException.class);
        }
    }
}
