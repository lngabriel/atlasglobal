package com.atlasglobal.integration.oecd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Small bounded RFC 4180 reader for the OECD SDMX CSV response. */
final class OecdCsv {
    private OecdCsv() {}
    private static final int MAX_CHARACTERS = 5_000_000;
    private static final int MAX_RECORDS = 50_000;

    static List<Map<String, String>> records(String body) {
        if (body == null || body.isBlank() || body.length() > MAX_CHARACTERS) {
            throw new OecdException("Missing or oversized OECD CSV response");
        }
        if (body.charAt(0) == '\ufeff') body = body.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        boolean started = false;
        for (int position = 0; position < body.length(); position++) {
            char current = body.charAt(position);
            if (quoted) {
                if (current == '"') {
                    if (position + 1 < body.length() && body.charAt(position + 1) == '"') {
                        field.append('"'); position++;
                    } else { quoted = false; closed = true; }
                } else field.append(current);
            } else if (current == ',') {
                row.add(field.toString()); field.setLength(0); closed = false; started = false;
            } else if (current == '\r' || current == '\n') {
                row.add(field.toString());
                if (row.stream().anyMatch(value -> !value.isBlank())) rows.add(List.copyOf(row));
                if (rows.size() > MAX_RECORDS) throw new OecdException("Oversized OECD CSV record count");
                row.clear(); field.setLength(0); closed = false; started = false;
                if (current == '\r' && position + 1 < body.length() && body.charAt(position + 1) == '\n') position++;
            } else if (current == '"' && !started && !closed) {
                quoted = true; started = true;
            } else {
                if (closed || current == '"') throw new OecdException("Malformed OECD CSV quoting");
                field.append(current); started = true;
            }
        }
        if (quoted) throw new OecdException("Unterminated OECD CSV quoted field");
        if (started || closed || !row.isEmpty() || !field.isEmpty()) {
            row.add(field.toString()); rows.add(List.copyOf(row));
        }
        if (rows.isEmpty()) throw new OecdException("Missing OECD CSV header");
        List<String> headers = rows.getFirst();
        if (headers.stream().anyMatch(String::isBlank) || new HashSet<>(headers).size() != headers.size()) {
            throw new OecdException("Invalid OECD CSV header");
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (List<String> values : rows.subList(1, rows.size())) {
            if (values.size() != headers.size()) throw new OecdException("Inconsistent OECD CSV record width");
            Map<String, String> record = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) record.put(headers.get(i), values.get(i));
            result.add(Map.copyOf(record));
        }
        return List.copyOf(result);
    }
}
