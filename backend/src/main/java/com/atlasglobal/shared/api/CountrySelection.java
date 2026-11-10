package com.atlasglobal.shared.api;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CountrySelection {
    private CountrySelection() {}
    public static List<String> parse(String countries) {
        var codes = Arrays.stream(countries.split(",", -1)).map(String::strip)
            .map(code -> code.toUpperCase(Locale.ROOT)).toList();
        if (codes.isEmpty() || codes.size() > 5 || codes.stream().anyMatch(code -> !code.matches("[A-Z0-9]{3}"))
            || codes.stream().distinct().count() != codes.size()) {
            throw ApiException.badRequest("Informe de um a cinco códigos de países distintos, separados por vírgulas.");
        }
        return codes;
    }
}
