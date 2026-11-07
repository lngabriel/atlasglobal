package com.atlasglobal.integration.worldbank;

import java.util.List;

public final class WorldBankCatalog {
    public record Entry(String code, String name, String category, String unit) {}
    public static final List<Entry> ENTRIES = List.of(
        new Entry("NY.GDP.MKTP.CD", "PIB", "ECONOMY", "USD_CURRENT"),
        new Entry("NY.GDP.PCAP.CD", "PIB per capita", "ECONOMY", "USD_CURRENT_PER_PERSON"),
        new Entry("NY.GDP.MKTP.KD.ZG", "Crescimento do PIB", "ECONOMY", "PERCENT_ANNUAL"),
        new Entry("SP.POP.TOTL", "População", "DEMOGRAPHY", "PERSONS"),
        new Entry("FP.CPI.TOTL.ZG", "Inflação ao consumidor", "ECONOMY", "PERCENT_ANNUAL"),
        new Entry("SL.UEM.TOTL.ZS", "Desemprego (estimativa modelada OIT)", "ECONOMY", "PERCENT_LABOR_FORCE"),
        new Entry("SP.DYN.LE00.IN", "Expectativa de vida ao nascer", "HEALTH", "YEARS"),
        new Entry("IT.NET.USER.ZS", "Pessoas que utilizam internet", "TECHNOLOGY", "PERCENT_POPULATION")
    );
    private WorldBankCatalog() {}
}
