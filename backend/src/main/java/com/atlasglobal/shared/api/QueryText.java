package com.atlasglobal.shared.api;

import java.util.Locale;

public final class QueryText {
    private QueryText() {}

    public static String exact(String text) {
        return text == null || text.isBlank() ? null : text.strip().toLowerCase(Locale.ROOT);
    }

    public static String contains(String text) {
        String normalized = exact(text);
        return normalized == null ? null
            : "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
