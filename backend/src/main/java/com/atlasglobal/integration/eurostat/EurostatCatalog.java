package com.atlasglobal.integration.eurostat;

import com.atlasglobal.ingestion.ProviderData;
import java.util.List;
import java.util.Map;

/** Fixed annual definitions keep ESA debt and renewable-energy shares separate from other sources. */
public final class EurostatCatalog {
    private EurostatCatalog() {}

    public static final ProviderData.Source SOURCE = new ProviderData.Source("EUROSTAT", "Eurostat",
        "https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/",
        "Official European statistics with original annual definitions and observation flags",
        "https://ec.europa.eu/eurostat/en/web/user-guides/data-browser/api-data-access/api-getting-started/api");

    public record Entry(ProviderData.Series definition, String dataset, String externalCode,
                        Map<String, String> dimensions, String originalName, String methodology) {
        ProviderData.Indicator metadata() {
            return new ProviderData.Indicator(externalCode, originalName, methodology, "Eurostat; national statistical authorities");
        }
    }

    public static final List<Entry> ENTRIES = List.of(
        new Entry(new ProviderData.Series("EUROSTAT.GOVERNMENT.DEBT.GDP", "Dívida bruta do governo geral (% do PIB, Eurostat)",
            "ECONOMY", "PERCENT_GDP", "GOV_10DD_EDPT1"), "gov_10dd_edpt1", "A.PC_GDP.S13.GD",
            Map.of("freq", "A", "unit", "PC_GDP", "sector", "S13", "na_item", "GD"),
            "General government consolidated gross debt (% of GDP, ESA 2010)",
            "Consolidated general government (S.13) gross debt at nominal face value outstanding at year-end, "
                + "as a percentage of annual GDP at current market prices. Includes currency and deposits (AF.2), "
                + "debt securities (AF.3), and loans (AF.4), following ESA 2010 and the Excessive Deficit Procedure. "
                + "Central, state, local government and social security funds are included. National coverage and revisions "
                + "remain relevant; this definition is not silently merged with IMF debt definitions. Original provider "
                + "flags are retained. Dataset update and collection dates are not observation-level revision dates. "
                + "https://ec.europa.eu/eurostat/cache/metadata/en/gov_10dd_esms.htm"),
        new Entry(new ProviderData.Series("EUROSTAT.RENEWABLE.ENERGY.SHARE", "Energia renovável no consumo final bruto (%, Eurostat)",
            "ENVIRONMENT", "PERCENT_GROSS_FINAL_ENERGY", "SDG_07_40"), "sdg_07_40", "A.REN.PC",
            Map.of("freq", "A", "nrg_bal", "REN", "unit", "PC"),
            "Share of renewable energy in gross final energy consumption, overall",
            "Renewable energy consumption as a percentage of gross final energy consumption, overall (REN), "
                + "compiled under the Renewable Energy Directive. Gross final energy consumption includes final energy "
                + "used by end consumers, grid losses, and power plants' own consumption. This is not the renewable "
                + "share of electricity generation or total primary energy. Original annual periods, decimal precision, "
                + "provisional/estimate/break flags, and national coverage are preserved. Directive and methodology changes "
                + "can affect historical comparability. Dataset update and collection dates are not observation-level revision dates. "
                + "https://ec.europa.eu/eurostat/cache/metadata/en/sdg_07_40_esmsip2.htm")
    );
}
