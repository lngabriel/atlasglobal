package com.atlasglobal.integration.oecd;

import com.atlasglobal.integration.CsvRecords;
import java.util.List;
import java.util.Map;

/** Keeps OECD-specific error handling around the shared RFC 4180 reader. */
final class OecdCsv {
    private OecdCsv() {}
    static List<Map<String, String>> records(String body) {
        try { return CsvRecords.records(body); }
        catch (IllegalStateException ex) { throw new OecdException(ex.getMessage(), ex); }
    }
}
