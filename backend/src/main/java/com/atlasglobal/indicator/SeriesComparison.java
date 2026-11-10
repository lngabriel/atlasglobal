package com.atlasglobal.indicator;

/** Curated restrictions accompany the original provider methodology in every response. */
public record SeriesComparison(boolean allowed, String note) {
    public static SeriesComparison forSeries(SeriesResponse series) {
        if ("IMF".equals(series.source().code())) {
            return new SeriesComparison(true,
                "Mesma edição do World Economic Outlook e mesma definição. Projeções do FMI são identificadas nos valores; o histórico também pode conter estimativas da equipe. Cobertura fiscal, anos de referência e revisões podem diferir entre países.");
        }
        if ("UNDP".equals(series.source().code())) {
            return new SeriesComparison(true,
                "Compare a mesma edição do Relatório de Desenvolvimento Humano. IDH e IDH ajustado à desigualdade são índices distintos de 0 a 1; as séries incorporam estimativas e revisões oficiais dos componentes e não medem diretamente renda ou salário.");
        }
        if ("EUROSTAT".equals(series.source().code())) {
            return new SeriesComparison(true,
                "Mesma definição e unidade do Eurostat, com cobertura europeia parcial. Valores provisórios, estimativas e quebras de série mantêm os sinalizadores da fonte. Dívida pública segue a definição de Maastricht; energia renovável usa o consumo final bruto de energia.");
        }
        if ("IBGE".equals(series.source().code())) {
            return new SeriesComparison(false,
                "Série nacional do Brasil, publicada pelo IBGE. A população pesquisada, a escala e a base de preços seguem a definição original; esta série não fornece observações harmonizadas de outros países.");
        }
        if ("BCB".equals(series.source().code())) {
            return new SeriesComparison(false,
                "Série nacional do Brasil: endividamento com o Sistema Financeiro Nacional em relação à RNDBF acumulada em 12 meses, observado em dezembro. Não equivale à razão dívida/renda disponível líquida da OCDE e não permite comparação direta entre países.");
        }
        if ("WORLD_BANK".equals(series.source().code())) {
            if ("SI.POV.NAHC".equals(series.externalCode())) {
                return new SeriesComparison(false,
                    "Linhas nacionais de pobreza usam limites próprios de cada país. A fonte não permite comparação direta entre países; consulte o histórico individual ou uma linha internacional em PPC.");
            }
            if ("SI.POV.GINI".equals(series.externalCode()) || series.externalCode().startsWith("SI.DST.")) {
                return new SeriesComparison(true,
                    "Pesquisas podem medir renda ou consumo e usar coberturas diferentes. Estes resultados não são estritamente comparáveis entre países ou anos, mesmo dentro da mesma série.");
            }
            if (series.externalCode().startsWith("SI.POV.")) {
                return new SeriesComparison(true,
                    "A linha internacional e a base PPC são as mesmas nesta série. Pesquisas de renda e consumo, cobertura e anos de levantamento podem diferir; não trate a comparação como um ranking completo.");
            }
            if (series.externalCode().startsWith("SM.POP.TOTL")) {
                return new SeriesComparison(true,
                    "Estoque de migrantes internacionais: a fonte prioriza país de nascimento e pode usar cidadania quando necessário. Não mede fluxo anual de imigração, emigração ou estritamente residentes de nacionalidade estrangeira.");
            }
            if ("VC.IHR.PSRC.P5".equals(series.externalCode())) {
                return new SeriesComparison(true,
                    "Homicídios intencionais por 100 mil habitantes. Cobertura e registro podem variar entre países; este indicador não representa criminalidade total nem todas as mortes violentas.");
            }
            if ("NY.GDP.PCAP.PP.CD".equals(series.externalCode())) {
                return new SeriesComparison(true,
                    "PIB por pessoa em dólares internacionais correntes, ajustado por paridade de poder de compra. Não mede salário, renda familiar ou poder de compra individual; valores correntes não corrigem a inflação ao longo do tempo.");
            }
        }
        if ("OECD".equals(series.source().code())) {
            return new SeriesComparison(true,
                "Compare apenas esta definição, unidade e base de preços. Cobertura nacional, trabalhadores abrangidos e regras de remuneração podem diferir; a metodologia original identifica as limitações.");
        }
        return new SeriesComparison(true,
            "A mesma série preserva fonte, definição e unidade. Verifique diferenças de cobertura e metodologia entre países e compare anos de referência compatíveis.");
    }
}
