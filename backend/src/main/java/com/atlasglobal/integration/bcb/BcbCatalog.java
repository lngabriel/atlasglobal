package com.atlasglobal.integration.bcb;

import com.atlasglobal.ingestion.ProviderData;

/** National definitions remain separate from international household-debt measures. */
public final class BcbCatalog {
    private BcbCatalog() {}

    public static final ProviderData.Source SOURCE = new ProviderData.Source("BCB", "Banco Central do Brasil",
        "https://api.bcb.gov.br/dados/serie/", "Sistema Gerenciador de Séries Temporais (SGS)",
        "https://dadosabertos.bcb.gov.br/dataset/29037-endividamento-das-familias-com-o-sistema-financeiro-nacional-em-relacao-a-renda-acumulada-dos");

    public static final ProviderData.Series DEFINITION = new ProviderData.Series("BCB.HOUSEHOLD.DEBT.DECEMBER",
        "Endividamento das famílias em dezembro (RNDBF, Banco Central)", "ECONOMY",
        "PERCENT_HOUSEHOLD_RNDBF_12M", "SGS");

    public static final ProviderData.Indicator METADATA = new ProviderData.Indicator("29037.DECEMBER",
        "Household debt with Brazil's financial system / restricted RNDBF, December observation",
        "The original BCB SGS series 29037 is monthly. Atlas Global retains only its reported December "
            + "observation for each completed calendar year, without averaging, interpolation or substituting "
            + "another month. Values are the percentage ratio of household debt with the Brazilian financial "
            + "system to restricted gross national disposable household income (RNDBF) accumulated over "
            + "the twelve months ending in the reference month. The restricted income definition excludes gross operating "
            + "surplus and property income. This is neither the percentage of indebted households nor the "
            + "OECD ratio to net disposable income. The current RNDBF-based source includes revised history "
            + "from 2005; the discontinued MSAD-based series 19882 is not spliced into it. "
            + "This national definition is available for Brazil only and is not a cross-country comparison. "
            + "Methodology: https://www.bcb.gov.br/content/estatisticas/hist_estatisticasmonetariascredito/"
            + "202112_Texto_de_estatisticas_monetarias_e_de_credito.pdf",
        "Banco Central do Brasil — Departamento de Estatísticas; SGS 29037");
}
