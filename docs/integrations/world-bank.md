# World Bank integration

## Official references

- [Indicators API documentation](https://datahelpdesk.worldbank.org/knowledgebase/articles/889392)
- [Basic call structures and paging](https://datahelpdesk.worldbank.org/knowledgebase/articles/898581-api-basic-call-structures)
- [Country queries](https://datahelpdesk.worldbank.org/knowledgebase/articles/898590-country-api-queries)
- [Indicator queries and source selection](https://datahelpdesk.worldbank.org/knowledgebase/articles/898599-indicator-api-queries)

The base URL is fixed to `https://api.worldbank.org/v2`. No API credentials are required. Visitor input cannot select arbitrary upstream URLs.

## Verified requests

These requests were exercised against the real API before implementation:

```text
GET /country?format=json&per_page=400&page=1
GET /indicator/NY.GDP.MKTP.CD?format=json&source=2
GET /country/BRA;USA;CHN;RUS;DEU;JPN;GBR;FRA;IND/indicator/NY.GDP.MKTP.CD?format=json&source=2&date=2022:2025&per_page=1000
```

Responses contain a two-element JSON array: pagination metadata and records. Observation metadata also includes `sourceid` and `lastupdated`. Country and observation fields use different naming conventions and have separate DTOs.

The client follows every page, checks page/record totals, and rejects pagination or source-update changes during a response sequence. WDI source `2` is explicit for both metadata and observation requests. Aggregates have region ID `NA` and are excluded. Empty coordinate strings become null.

## Socioeconomic series

The catalog includes the original eight indicators and the following sixteen annual series from WDI source `2`. Codes, source metadata, and recent observations were verified against the official API on 2026-11-09. Values are stored exactly as published, without deriving additional measures.

| Indicator | Code | Stored unit |
| --- | --- | --- |
| GDP per capita at purchasing power parity | `NY.GDP.PCAP.PP.CD` | Current international dollars per person |
| Gini index | `SI.POV.GINI` | Index from 0 to 100 |
| Poverty below $3.00/day, 2021 PPP | `SI.POV.DDAY` | Percent of population |
| Poverty below $4.20/day, 2021 PPP | `SI.POV.LMIC` | Percent of population |
| Poverty below $8.30/day, 2021 PPP | `SI.POV.UMIC` | Percent of population |
| Poverty at national poverty lines | `SI.POV.NAHC` | Percent of population |
| Income or consumption share, lowest quintile | `SI.DST.FRST.20` | Percent of income or consumption |
| Income or consumption share, second quintile | `SI.DST.02ND.20` | Percent of income or consumption |
| Income or consumption share, third quintile | `SI.DST.03RD.20` | Percent of income or consumption |
| Income or consumption share, fourth quintile | `SI.DST.04TH.20` | Percent of income or consumption |
| Income or consumption share, highest quintile | `SI.DST.05TH.20` | Percent of income or consumption |
| Income or consumption share, highest decile | `SI.DST.10TH.10` | Percent of income or consumption |
| Intentional homicides | `VC.IHR.PSRC.P5` | Homicides per 100,000 population |
| Net migration | `SM.POP.NETM` | Persons |
| International migrant stock | `SM.POP.TOTL` | Persons |
| International migrant stock share | `SM.POP.TOTL.ZS` | Percent of population |

The poverty thresholds use **2021 purchasing power parity**; these series must not be mixed with older editions using other thresholds or PPP bases. Gini and income shares reflect household income or consumption surveys with differences in welfare measures and survey methods. [National poverty lines](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/SI.POV.NAHC) are country-specific and must not be used for direct cross-country poverty comparisons. The [Gini metadata](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/SI.POV.GINI) explains additional limits on comparisons across countries and years.

GDP per capita in PPP is an economic-output measure adjusted for price-level differences. It is neither a wage nor a direct measurement of household purchasing power. Intentional homicide is a specific UNODC definition; it does not represent a general crime rate or all violent deaths.

[Net migration](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/SM.POP.NETM) is the annual number of immigrants minus emigrants, including citizens and noncitizens. It is not a separate immigration or emigration rate. [Migrant stock](https://databank.worldbank.org/metadataglossary/world-development-indicators/series/SM.POP.TOTL) is primarily the foreign-born population, including refugees, with citizenship-based substitutes in some economies. It is not interchangeable with foreign-national resident counts. Its percentage uses the UN population denominator, so counts must not be reconstructed by multiplying the percentage by WDI population. Survey and census years remain sparse; gaps are preserved.

## Published world aggregates

Each import also retrieves the official `WLD` aggregate for GDP, population, life expectancy, and Internet use. These observations are stored in `world_indicator_observation`, separately from countries and country histories. `WLD` is never added to the country catalog. The read-only `/api/v1/overview` endpoint returns these published values and their original reference years and provenance; it does not calculate a world total or average from the countries selected in the interface.

World observations participate in the same provider lock and import execution. Their processed rows contribute to `records_imported`, including missing values and updates, and the execution scope records `world=WLD`. Repeated imports update `(series_id, period)` keys without duplicating data.

## Resilience

Defaults: 500 rows per page, 10-second connection timeout, 45-second read timeout, and at most three attempts. Retries apply to connection failures, HTTP 429, and HTTP 5xx. Permanent HTTP failures, malformed JSON, provider error envelopes, incomplete results, and incompatible identifiers fail immediately.

Backoff is bounded. `Retry-After` supports seconds and HTTP dates; waits beyond one minute fail the current command for an operational retry later. Interrupted waits restore the thread interrupt flag. Source payloads and stack traces are not placed in persisted error messages.

## Operations

The import command and optional Spring scheduler call the same service. No import controller or administrative route exists. Startup without the import flag and without scheduling performs no provider calls.

The catalog imports twenty-four indicators for the configured countries, while importing all nonaggregate country metadata and the four world series described above. The configured country list can include any eligible country code returned by World Bank, without code changes. Imports accept 1–50 distinct country codes per execution. Taiwan is not available through the WDI country endpoint and must not be requested through this adapter.

Manual execution uses the configured historical interval. The weekly scheduler refreshes the most recent six-year window within that interval. Full historical reconciliations use the same manual command. Scheduled execution is disabled by default and is always suppressed in one-shot import processes.

Use the commands in the root README to inspect import status and coverage. Do not interpret the imported subset as a complete world ranking.

## Validation

On 2026-11-06, a real historical import into an isolated PostgreSQL 17 database stored 217 nonaggregate countries, eight indicator definitions, and 4,752 observations for the nine configured countries. Of those observations, 4,007 had numeric values and 745 were explicitly missing. These counts describe that provider snapshot, not a permanent coverage guarantee.

Test fixtures are derived from real responses captured on that date. The country fixture is shortened to Brazil, the United States, and the World aggregate, with its total adjusted for the test. Controlled unit/error/pagination fixtures exist only in tests; they are never loaded by the application.

The full historical import was repeated successfully: the database still contained 4,752 unique country/series/year observations. A persisted Brazil GDP observation for 2025 matched the official response value exactly (`2279920092492.13`).

Automated test results are recorded in the [validation report](../validation.md). Backend and frontend Docker images built successfully, and an isolated three-service Compose deployment returned readiness UP. The documented container import command completed a separate 2022–2024 import with 216 observations. The frontend served its SPA fallback route through Nginx.

A command requesting an unavailable country returned exit code 1 and persisted a FAILED import with zero processed records, while the previous successful import remained intact.
