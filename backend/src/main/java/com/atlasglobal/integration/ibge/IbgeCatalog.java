package com.atlasglobal.integration.ibge;

import com.atlasglobal.ingestion.ProviderData;
import java.util.List;

/** National PNAD definitions remain distinct from internationally harmonized indicators. */
public final class IbgeCatalog {
    private IbgeCatalog() {}

    public static final ProviderData.Source SOURCE = new ProviderData.Source("IBGE", "IBGE",
        "https://servicodados.ibge.gov.br/api/v3/agregados/", "IBGE PNAD Contínua anual — SIDRA",
        "https://servicodados.ibge.gov.br/api/docs/agregados?versao=3");

    public record Entry(String code, String name, String table, String variable, String originalName,
                        String category, String sourceUnit, boolean realIncome) {
        String externalCode() { return variable + (realIncome ? ".C1019.49243" : ""); }

        ProviderData.Series definition(int publishedEndYear) {
            return new ProviderData.Series(code, name, category,
                realIncome ? "BRL_REAL_" + publishedEndYear + "_MONTHLY_PER_PERSON" : "GINI_COEFFICIENT",
                "SIDRA_" + table + (realIncome ? ":" + publishedEndYear : ""));
        }

        ProviderData.Indicator metadata(int publishedEndYear) {
            String common = "IBGE Continuous National Household Sample Survey (PNAD Contínua), annual publication, "
                + "Brazil national geography N1=1. The source consolidates first household visits for 2012–2019 "
                + "and from 2023, and fifth visits for 2020–2022; survey design and revisions must be considered "
                + "when interpreting changes. These are official survey estimates, not estimates made by Atlas Global. ";
            String definition = realIncome
                ? "Mean monthly real household income per resident, all income sources, total population category "
                    + "1019=49243. Values are published by IBGE at average prices of the last published year, "
                    + publishedEndYear + ", identified from the complete table's annual metadata rather than the requested "
                    + "display interval. The monthly amount is an annual survey statistic, not an annual salary, "
                    + "employee wage or purchasing-power-parity conversion. New price bases form separate series. "
                : "Gini coefficient of monthly household income per capita, at average prices of the reference year. "
                    + "The IBGE coefficient is preserved on its original 0–1 scale; it is not the World Bank's 0–100 "
                    + "series. The year denotes the annual survey reference, not the collection date of this import. ";
            return new ProviderData.Indicator(externalCode(), originalName, common + definition
                + "Source table: https://sidra.ibge.gov.br/tabela/" + table,
                "Instituto Brasileiro de Geografia e Estatística (IBGE), PNAD Contínua anual");
        }
    }

    public static final List<Entry> ENTRIES = List.of(
        new Entry("IBGE.GINI.HOUSEHOLD.PER.CAPITA", "Índice de Gini domiciliar per capita (PNAD Contínua)",
            "7435", "10681", "Índice de Gini do rendimento domiciliar per capita, a preços médios do ano",
            "INEQUALITY", "Índice", false),
        new Entry("IBGE.HOUSEHOLD.INCOME.PER.CAPITA", "Rendimento domiciliar mensal real per capita (PNAD Contínua)",
            "7533", "10816", "Rendimento médio mensal real domiciliar per capita, a preços médios do último ano",
            "ECONOMY", "Reais", true)
    );
}
