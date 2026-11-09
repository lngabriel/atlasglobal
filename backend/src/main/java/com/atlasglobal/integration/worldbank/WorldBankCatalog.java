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
        new Entry("IT.NET.USER.ZS", "Pessoas que utilizam internet", "TECHNOLOGY", "PERCENT_POPULATION"),
        new Entry("NY.GDP.PCAP.PP.CD", "PIB per capita em paridade de poder de compra", "ECONOMY",
            "INTERNATIONAL_DOLLARS_CURRENT_PER_PERSON"),
        new Entry("SI.POV.GINI", "Índice de Gini", "INEQUALITY", "GINI_INDEX"),
        new Entry("SI.POV.DDAY", "Pobreza abaixo de US$ 3,00/dia (PPC de 2021)", "INEQUALITY", "PERCENT_POPULATION"),
        new Entry("SI.POV.LMIC", "Pobreza abaixo de US$ 4,20/dia (PPC de 2021)", "INEQUALITY", "PERCENT_POPULATION"),
        new Entry("SI.POV.UMIC", "Pobreza abaixo de US$ 8,30/dia (PPC de 2021)", "INEQUALITY", "PERCENT_POPULATION"),
        new Entry("SI.POV.NAHC", "Pobreza segundo a linha nacional", "INEQUALITY", "PERCENT_POPULATION"),
        new Entry("SI.DST.FRST.20", "Renda ou consumo: participação dos 20% mais pobres", "INEQUALITY", "PERCENT_INCOME"),
        new Entry("SI.DST.02ND.20", "Renda ou consumo: participação do segundo quintil", "INEQUALITY", "PERCENT_INCOME"),
        new Entry("SI.DST.03RD.20", "Renda ou consumo: participação do terceiro quintil", "INEQUALITY", "PERCENT_INCOME"),
        new Entry("SI.DST.04TH.20", "Renda ou consumo: participação do quarto quintil", "INEQUALITY", "PERCENT_INCOME"),
        new Entry("SI.DST.05TH.20", "Renda ou consumo: participação dos 20% mais ricos", "INEQUALITY", "PERCENT_INCOME"),
        new Entry("SI.DST.10TH.10", "Renda ou consumo: participação dos 10% mais ricos", "INEQUALITY", "PERCENT_INCOME"),
        new Entry("VC.IHR.PSRC.P5", "Homicídios intencionais por 100 mil habitantes", "SAFETY", "HOMICIDES_PER_100K"),
        new Entry("SM.POP.NETM", "Saldo migratório", "MIGRATION", "PERSONS"),
        new Entry("SM.POP.TOTL", "Estoque de migrantes internacionais", "MIGRATION", "PERSONS"),
        new Entry("SM.POP.TOTL.ZS", "Migrantes internacionais na população", "MIGRATION", "PERCENT_POPULATION")
    );
    public static final List<String> WORLD_INDICATORS = List.of(
        "NY.GDP.MKTP.CD", "SP.POP.TOTL", "SP.DYN.LE00.IN", "IT.NET.USER.ZS");
    private WorldBankCatalog() {}
}
