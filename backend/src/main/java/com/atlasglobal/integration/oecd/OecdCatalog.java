package com.atlasglobal.integration.oecd;

import com.atlasglobal.ingestion.ProviderData;
import java.util.List;

/** Explicit selections prevent silent mixing of earnings periods, price bases or currencies. */
public final class OecdCatalog {
    private OecdCatalog() {}

    public static final ProviderData.Source SOURCE = new ProviderData.Source("OECD", "OECD",
        "https://sdmx.oecd.org/public/rest/v1/", "OECD official annual socioeconomic statistics",
        "https://www.oecd.org/en/data/insights/data-explainers/2024/09/api.html");

    enum Kind { AVERAGE_WAGE, MINIMUM_WAGE, HOUSEHOLD_DEBT }

    public record Entry(String code, String name, String agency, String dataset, String key,
                        String externalCode, Kind kind) {
        String dataflow() { return agency + ":" + dataset + "(1.0)"; }
        boolean ppp() { return kind != Kind.HOUSEHOLD_DEBT; }

        ProviderData.Series definition(String baseYear) {
            String unit = ppp() ? "USD_PPP_" + baseYear + "_ANNUAL" : "PERCENT_NET_DISPOSABLE_INCOME";
            String versionedDataset = dataset + (ppp() ? ":" + baseYear : "");
            return new ProviderData.Series(code, name, "ECONOMY", unit, versionedDataset);
        }

        ProviderData.Indicator metadata(String baseYear) {
            return switch (kind) {
                case AVERAGE_WAGE -> new ProviderData.Indicator(externalCode,
                    "Average annual wages (constant " + baseYear + " USD PPP)",
                    "Average gross annual wages per full-time-equivalent dependent employee in the total economy. "
                        + "The national-accounts wage bill is divided by employee numbers and adjusted using usual "
                        + "working hours to a full-time equivalent. Values are OECD-reported constant " + baseYear
                        + " US dollars converted using purchasing-power parities. These are annual gross amounts, "
                        + "not monthly take-home pay; country coverage varies. "
                        + "Definition: https://www.oecd.org/en/data/indicators/average-annual-wages.html",
                    "OECD National Accounts and Labour Market Statistics");
                case MINIMUM_WAGE -> new ProviderData.Indicator(externalCode,
                    "Real annual statutory minimum wages (constant " + baseYear + " USD PPP)",
                    "Statutory minimum wages expressed by the OECD as a common annual pay period. "
                        + "The series is deflated using the consumer price index and converted using purchasing-power "
                        + "parities for private consumption, in constant " + baseYear + " US dollars. "
                        + "These annual amounts are not monthly wages, actual earnings or a guarantee of a universal "
                        + "minimum in every country. Coverage and statutory arrangements differ. "
                        + "Definition: https://data-explorer.oecd.org/vis?df[ag]=OECD.ELS.SAE&df[id]=DSD_EARNINGS@RMW",
                    "OECD Earnings database; national statutory minimum wage statistics");
                case HOUSEHOLD_DEBT -> new ProviderData.Indicator(externalCode,
                    "Debt of households and NPISH (% of net disposable income)",
                    "Liabilities of households and non-profit institutions serving households (NPISH) that require "
                        + "future principal or interest payments, including loans and other accounts payable. "
                        + "The denominator is net household disposable income. The value is not the share of "
                        + "households with debt and is not debt as a percentage of GDP. "
                        + "Definition: https://www.oecd.org/en/data/indicators/household-debt.html",
                    "OECD National Accounts Statistics");
            };
        }
    }

    public static final List<Entry> ENTRIES = List.of(
        new Entry("OECD.WAGE.AVERAGE.PPP", "Salário médio anual (PPC, preços constantes)",
            "OECD.ELS.SAE", "DSD_EARNINGS@AV_AN_WAGE", "all", "WG.USD_PPP.A.Q.MEAN._Z", Kind.AVERAGE_WAGE),
        new Entry("OECD.WAGE.MINIMUM.PPP", "Salário mínimo anual (PPC, preços constantes)",
            "OECD.ELS.SAE", "DSD_EARNINGS@RMW", "all", "SM_WG.USD_PPP.A.Q._Z._Z", Kind.MINIMUM_WAGE),
        new Entry("OECD.HOUSEHOLD.DEBT", "Dívida das famílias (% da renda disponível líquida)",
            "OECD.SDD.NAD", "DSD_NAAG@DF_NAAG", "A..LES1M_FD4..", "LES1M_FD4.PT_B6N_S1M.NAAG_V", Kind.HOUSEHOLD_DEBT)
    );
}
