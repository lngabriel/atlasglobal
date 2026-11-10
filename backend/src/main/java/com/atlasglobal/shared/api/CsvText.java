package com.atlasglobal.shared.api;

/** RFC 4180 text cells with a prefix for formula-like spreadsheet inputs. Numeric cells are separate. */
public final class CsvText {
    private CsvText() {}
    public static String cell(String value) {
        if (value == null) return "\"\"";
        String leading = value.stripLeading();
        boolean control = !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0;
        boolean formula = !leading.isEmpty() && "=+-@＝＋－＠".indexOf(leading.charAt(0)) >= 0;
        return "\"" + (control || formula ? "\t" + value : value).replace("\"", "\"\"") + "\"";
    }
}
