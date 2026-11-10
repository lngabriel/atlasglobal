# Data coverage and comparability

Atlas Global stores annual series published by official providers. A catalog entry does not guarantee that a country has observations, that every year is present, or that the most recent year matches another series. The interface preserves each value's reference year, unit, provider, methodology, and collection date. Missing observations and years without a record remain unavailable; neither is converted to zero.

The catalog defines 30 indicators: 24 WDI definitions, three OECD definitions, two IBGE definitions, and one BCB definition. National sources complement international coverage while retaining their own indicator identities.

## Supported measures

| Subject | Published measure | Interpretation |
| --- | --- | --- |
| Household indebtedness | OECD debt of households and non-profit institutions serving households, as a percentage of net disposable income | A debt-to-income ratio; neither the percentage of indebted households nor debt as a share of GDP. |
| Brazilian household indebtedness | BCB household debt with the national financial system, relative to restricted gross national disposable household income (RNDBF) accumulated over twelve months | The actual December observation for each completed year. The restricted denominator excludes gross operating surplus and property income; this is not the OECD ratio to net disposable income. |
| Average wages | OECD gross average annual wages per full-time-equivalent dependent employee, in constant US dollars at PPP | Annual gross remuneration, not monthly take-home pay. The price base is part of the series definition. |
| Minimum wages | OECD real annual statutory minimum wages, in constant US dollars at PPP | A common annual pay period; statutory arrangements and country coverage differ. A missing observation does not establish that a country has no minimum wage. |
| Purchasing-power context | WDI GDP per capita in current international dollars at PPP | Economic output per person adjusted for price levels. This is neither a wage nor a direct index of household buying power. |
| Inequality | WDI Gini index, from 0 to 100 | The source's index scale is preserved. Zero means equality; 100 means maximal inequality. |
| Brazilian inequality | IBGE PNAD Contínua Gini coefficient of monthly household income per capita, from 0 to 1 | A distinct national survey definition, displayed to three decimal places without rescaling to the WDI index. |
| Brazilian household income | IBGE PNAD Contínua mean real monthly household income per person, at average prices of the last published year | Includes all income sources. A monthly amount reported for an annual survey reference; it is not a wage or a PPP measure. The price base is retained in the series identity. |
| International poverty | WDI population shares below $3.00, $4.20, and $8.30 per day, at 2021 PPP | Each threshold is a separate series. These are percentages, not population counts. |
| National poverty | WDI population share below the country's own national poverty line | Suitable for the country's historical context. Direct country comparisons are disabled. |
| Distribution | WDI income or consumption shares held by each population quintile and the highest decile | Shares of the welfare measure used by the underlying survey; quintiles may not total exactly 100 because of source rounding. |
| Safety | WDI/UNODC intentional homicides per 100,000 people | A specific recorded-death definition; not total crime or all violent deaths. |
| Migration balance | WDI/UN annual net migration, in persons | Immigrants minus emigrants, including citizens and noncitizens. |
| Migrant presence | WDI/UN international migrant stock, in persons and as a population percentage | Primarily foreign-born residents, including refugees; some economies use citizenship-based substitutes. These measures are stocks, not annual migration flows. |

See the [World Bank adapter reference](integrations/world-bank.md) and [Brazilian adapter reference](integrations/brazil.md) for exact source codes. OECD monetary and IBGE real-income series keep their price base in the stored unit and dataset identity; values from different bases are not merged into one series.

## Comparison rules

A country comparison uses one explicit source-specific series ID, with one indicator definition, unit, dataset, frequency, and price basis. Separate definitions are never combined silently. Countries without observations remain visible as unavailable, and the interface does not carry an older value forward to fill a selected year.

The public series response includes `comparison.allowed` and `comparison.note`. National poverty lines are blocked for direct country comparisons because their thresholds are country-specific. IBGE and BCB definitions are also blocked: they cover Brazil, not a harmonized set of countries. Their observations remain available in the Brazilian profile. Other restrictions remain visible as methodological context:

- **Gini and distribution:** household surveys may measure income or consumption and use different coverage. Sharing a provider and code does not make those surveys strictly comparable across countries or years.
- **International poverty:** compare one threshold and the same PPP edition. Earlier WDI editions used different thresholds and PPP bases; those figures must not be spliced into the current series. Survey timing and welfare concepts still vary.
- **Wages:** compare a common annual gross measure and a common constant-price PPP base. Workers covered, full-time adjustments, and statutory arrangements may differ.
- **Migration:** net flow, migrant stock, foreign-born population, and foreign citizenship are different concepts. The migrant-stock percentage uses the UN population denominator; multiplying it by WDI population can yield a different count and is not performed.
- **Safety:** recording systems, coverage, and reporting years may differ even under the intentional-homicide definition. Absence is not evidence of zero violence.
- **Current-price GDP:** current US dollars and current international dollars preserve price changes over time. A nominal increase is not automatically real growth or an increase in household welfare.
- **Brazilian PNAD series:** the survey combines first household visits for 2012–2019 and from 2023, and fifth visits for 2020–2022. Survey design and revisions remain relevant even when interpreting a national history. Household per-capita income does not measure employee remuneration.
- **Brazilian indebtedness:** December is a year-end snapshot, not an annual mean. Current-year monthly values are not presented as complete annual observations. The current RNDBF-based SGS series is kept separate from the discontinued MSAD-based definition.

These controls support careful exploration; they do not establish a complete country ranking.

## Reference years and freshness

An observation's year describes the statistical reference period, not the date when Atlas Global collected it. A provider may update its database while an individual country/indicator still has no newer numeric observation. The profile retains the latest available numeric value and identifies later records reported as missing; the collection date remains separate.

A constant-price base is also separate from the observation year. IBGE real-income tables publish their complete history at average prices of the last published year. The importer reads that year from the full table metadata and checks it against the latest returned period, independently of the requested display interval. Rebasing creates a separate series. The World Bank's 2021 PPP poverty basis likewise does not mean that every observation refers to 2021.

National sources can have different release schedules and definitions from international compilations. They are not automatic replacements for international series. Unknown statistical revision dates remain null; HTTP resource and catalog-maintenance dates are not used as substitutes.

## Measures without a validated integrated series

The application does not manufacture substitutes for the following requested concepts:

| Requested concept | Current limitation |
| --- | --- |
| Number of people below a poverty line | The integrated WDI series supply population percentages. A count is not estimated from a separately sourced population denominator. |
| General crime rate | No single integrated definition covers the same offences and recording practices across the selected countries. Intentional homicide remains separately identified. |
| All violent deaths | The intentional-homicide series does not cover all conflict deaths, deaths from legal intervention, suicide, or other categories of violent death. |
| Separate immigration and emigration rates | The integrated net-migration and migrant-stock series do not provide separate flows or uniform rate denominators. |
| Strict foreign-national resident population | International migrant stock is mainly based on place of birth and cannot be relabeled as nationality. |
| Direct household purchasing-power index | GDP per capita at PPP describes economic output and price-level differences, not a household's disposable income or consumption basket. |

Official source estimates remain identified through their original methodology. Atlas Global does not create its own estimates or interpolate missing years.

## Countries, territories, and world aggregates

The home selection is editorial navigation, not a definition of worldwide coverage. The complete searchable catalog remains separate from the selection. Taiwan is retained as a catalog identity from the [IMF country catalog](https://www.imf.org/external/datamapper/api/v1/countries); this does not create WDI observations for Taiwan or infer unavailable demographic metadata. A country's missing provider coverage is reported explicitly.

IBGE and BCB summaries appear only for Brazil. The public indicator catalog still exposes these definitions and their coverage notes. An explicit single-country historical request outside that national coverage returns an empty observation collection rather than inventing values. International definitions remain visible when a country's observations are missing.

The global highlights use the World Bank's published `WLD` observations for GDP, population, life expectancy, and Internet use. They are stored separately from country observations. They are not sums or averages of the home selection, and their reporting years can differ.

## Official methodology references

- [OECD average annual wages](https://www.oecd.org/en/data/indicators/average-annual-wages.html)
- [OECD real minimum wages](https://data-explorer.oecd.org/vis?df[ag]=OECD.ELS.SAE&df[id]=DSD_EARNINGS@RMW)
- [OECD household debt](https://www.oecd.org/en/data/indicators/household-debt.html)
- [World Bank Gini metadata](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/SI.POV.GINI)
- [World Bank international poverty metadata](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/SI.POV.DDAY)
- [World Bank national poverty metadata](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/SI.POV.NAHC)
- [World Bank income-distribution metadata](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/SI.DST.FRST.20)
- [World Bank intentional-homicide metadata](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/VC.IHR.PSRC.P5)
- [World Bank net-migration metadata](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/SM.POP.NETM)
- [World Bank migrant-stock metadata](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/SM.POP.TOTL)
- [World Bank GDP per capita at PPP](https://data.worldbank.org/indicator/NY.GDP.PCAP.PP.CD)
- [IBGE PNAD Contínua Gini table 7435](https://sidra.ibge.gov.br/tabela/7435)
- [IBGE PNAD Contínua real household income table 7533](https://sidra.ibge.gov.br/tabela/7533)
- [BCB household indebtedness, SGS 29037](https://dadosabertos.bcb.gov.br/dataset/29037-endividamento-das-familias-com-o-sistema-financeiro-nacional-em-relacao-a-renda-acumulada-dos)
