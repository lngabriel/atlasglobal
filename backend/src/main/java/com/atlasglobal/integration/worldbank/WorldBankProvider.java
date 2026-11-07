package com.atlasglobal.integration.worldbank;

import com.atlasglobal.ingestion.DataProvider;
import com.atlasglobal.ingestion.ProviderData;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class WorldBankProvider implements DataProvider {
    private final WorldBankClient client;
    public WorldBankProvider(WorldBankClient client) { this.client = client; }
    @Override public String code() { return "WORLD_BANK"; }

    @Override
    public List<ProviderData.Country> countries() {
        return client.countries().stream()
            .filter(c -> c.region() != null && !"NA".equals(c.region().id()))
            .map(this::normalizeCountry).toList();
    }

    ProviderData.Country normalizeCountry(WorldBankDtos.Country c) {
        if (c.id() == null || !c.id().matches("[A-Z0-9]{3}") || c.iso2Code() == null
            || !c.iso2Code().matches("[A-Z0-9]{2}") || c.name() == null || c.name().isBlank()) {
            throw new WorldBankException("Invalid World Bank country identifiers");
        }
        String translated = Locale.of("", c.iso2Code()).getDisplayCountry(Locale.forLanguageTag("pt-BR"));
        if (translated.isBlank() || translated.equals(c.iso2Code())) translated = c.name();
        BigDecimal latitude = coordinate(c.latitude(), 90);
        BigDecimal longitude = coordinate(c.longitude(), 180);
        return new ProviderData.Country(c.iso2Code(), c.id(), translated, c.name(),
            c.region() == null ? null : c.region().value().strip(),
            c.incomeLevel() == null ? null : c.incomeLevel().value().strip(),
            emptyToNull(c.capitalCity()), latitude, longitude);
    }

    @Override
    public ProviderData.Indicator indicator(String code) {
        var value = client.indicator(code);
        if (value.name() == null || value.name().isBlank() || value.sourceNote() == null || value.sourceNote().isBlank()) {
            throw new WorldBankException("Incomplete World Bank indicator metadata: " + code);
        }
        if (value.unit() != null && !value.unit().isBlank()) {
            throw new WorldBankException("World Bank unit changed; review the catalog mapping: " + code);
        }
        return new ProviderData.Indicator(code, value.name(), value.sourceNote(),
            value.sourceOrganization() == null ? "" : value.sourceOrganization());
    }

    @Override
    public void observations(List<String> countries, String indicator, int from, int to,
                             Consumer<ProviderData.Batch> consumer) {
        client.observations(countries, indicator, from, to, page -> {
            if (!"2".equals(page.sourceId()) || page.updatedAt() == null) {
                throw new WorldBankException("Missing or incompatible World Bank source metadata");
            }
            var observations = page.records().stream().map(row -> {
                if (!countries.contains(row.countryiso3code()) || row.indicator() == null
                    || !indicator.equals(row.indicator().id()) || row.date() == null || !row.date().matches("[0-9]{4}")) {
                    throw new WorldBankException("Observation does not match the requested World Bank series");
                }
                int year = Integer.parseInt(row.date());
                if (year < from || year > to) throw new WorldBankException("Observation outside requested interval");
                if (row.unit() != null && !row.unit().isBlank()) {
                    throw new WorldBankException("World Bank observation unit changed; review the catalog mapping");
                }
                // WDI annual values use the native units described in the indicator metadata.
                return new ProviderData.Observation(row.countryiso3code(), indicator, year, row.value(), row.obs_status());
            }).toList();
            consumer.accept(new ProviderData.Batch(observations, page.updatedAt()));
        });
    }

    private BigDecimal coordinate(String value, int bound) {
        if (value == null || value.isBlank()) return null;
        try {
            var decimal = new BigDecimal(value);
            if (decimal.abs().compareTo(BigDecimal.valueOf(bound)) > 0) throw new NumberFormatException();
            return decimal;
        } catch (NumberFormatException ex) { throw new WorldBankException("Invalid World Bank coordinate", ex); }
    }
    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
