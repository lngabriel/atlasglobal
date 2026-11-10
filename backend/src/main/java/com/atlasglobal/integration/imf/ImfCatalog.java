package com.atlasglobal.integration.imf;

import com.atlasglobal.ingestion.ProviderData;
import java.util.List;

/** WEO definitions stay separate from World Bank statistics and retain their release identity. */
public final class ImfCatalog {
    private ImfCatalog() {}

    public static final ProviderData.Source SOURCE = new ProviderData.Source("IMF", "International Monetary Fund",
        "https://www.imf.org/external/datamapper/api/v2/", "IMF World Economic Outlook annual statistics and projections",
        "https://www.imf.org/external/datamapper/api/");

    public record Entry(String code, String name, String externalCode, String originalName) {}

    public static final List<Entry> ENTRIES = List.of(
        new Entry("IMF.GOVERNMENT.DEBT.GDP", "Dívida bruta do governo geral (% do PIB)", "GGXWDG_NGDP", "General government gross debt"),
        new Entry("IMF.CURRENT.ACCOUNT.GDP", "Saldo em conta corrente (% do PIB)", "BCA_NGDPD", "Current account balance, percent of GDP")
    );
}
