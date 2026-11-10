# Eurostat integration

Atlas Global imports two annual definitions from Eurostat's official Statistics API. They retain their own indicator codes, units, source metadata, and observation flags. They are not combined with similarly named World Bank or IMF measures. Public queries read PostgreSQL and never initiate provider requests.

## Official references

- [Statistics API getting-started guide](https://ec.europa.eu/eurostat/en/web/user-guides/data-browser/api-data-access/api-getting-started/api)
- [Statistics API request and filter reference](https://ec.europa.eu/eurostat/en/web/user-guides/data-browser/api-data-access/api-detailed-guidelines/api-statistics)
- [API availability and dataset updates](https://ec.europa.eu/eurostat/en/web/user-guides/data-browser/api-data-access/api-introduction)
- [Government deficit and debt methodology](https://ec.europa.eu/eurostat/cache/metadata/en/gov_10dd_esms.htm)
- [Renewable-energy share methodology](https://ec.europa.eu/eurostat/cache/metadata/en/sdg_07_40_esmsip2.htm)
- [Data Browser flags](https://ec.europa.eu/eurostat/en/web/user-guides/data-browser/data-selection-display/tables)

The base URL is fixed to `https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/`. No API key or additional dependency is required. Visitor input cannot select an upstream host or dataset.

## Selected annual definitions

| Internal indicator | Dataset | Fixed dimension selection | Stored unit |
| --- | --- | --- | --- |
| `EUROSTAT.GOVERNMENT.DEBT.GDP` | `gov_10dd_edpt1` | `freq=A`, `unit=PC_GDP`, `sector=S13`, `na_item=GD` | `PERCENT_GDP` |
| `EUROSTAT.RENEWABLE.ENERGY.SHARE` | `sdg_07_40` | `freq=A`, `nrg_bal=REN`, `unit=PC` | `PERCENT_GROSS_FINAL_ENERGY` |

The stored dataset identities are `GOV_10DD_EDPT1` and `SDG_07_40`; the external series codes are `A.PC_GDP.S13.GD` and `A.REN.PC`, respectively. Responses must match those identities and selected dimensions before any observations are accepted.

### General government debt

This is consolidated general government gross debt at nominal face value outstanding at year-end, divided by annual GDP at current market prices. General government (`S13`) includes central, state, and local government and social security funds. The debt instruments are currency and deposits (`AF.2`), debt securities (`AF.3`), and loans (`AF.4`), following ESA 2010 and the Excessive Deficit Procedure.

It is a debt stock relative to an annual GDP flow, not the annual budget deficit, household debt, or a central-government-only measure. Its consolidation, valuation, instrument coverage, and accounting framework must be considered before comparing it with another provider's debt definition. Atlas Global retains a separate source-specific series.

### Renewable energy

The selected overall renewable-energy share (`REN`) measures renewable consumption as a percentage of gross final energy consumption under the Renewable Energy Directive. The denominator includes final energy consumed by end users, grid losses, and power plants' own consumption.

This is not the renewable share of electricity generation or total primary energy. Directive changes, national coverage, and source revisions can affect historical comparisons. Published decimal precision and observation flags are retained; the application does not calculate its own energy share.

## Requests and country mapping

Production requests select `lang=EN`, `geoLevel=country`, and inclusive `sinceTimePeriod`/`untilTimePeriod` bounds, together with the fixed dimensions above. For example:

```text
https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/gov_10dd_edpt1?lang=EN&geoLevel=country&freq=A&sector=S13&unit=PC_GDP&na_item=GD&sinceTimePeriod=2020&untilTimePeriod=2025
https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/sdg_07_40?lang=EN&geoLevel=country&freq=A&unit=PC&nrg_bal=REN&sinceTimePeriod=2020&untilTimePeriod=2025
```

The adapter requests the bounded country-level slice and then retains only configured countries already present in Atlas Global's catalog. It does not request European aggregates or add country identities from the provider response. Eurostat's `geo` and `geoLevel` parameters are mutually exclusive, so these requests use `geoLevel` alone.

Country mapping uses the persisted catalog's ISO2 identifiers, with explicit Eurostat exceptions: Greece's `GR` maps to `EL`, and the United Kingdom's `GB` maps to `UK`. The original ISO3 country identity remains the observation key. Mapping a country code does not guarantee that a dataset contains that country or the requested period.

Real responses checked on **2026-11-10** contained debt history for 27 EU countries, covering periods within 1995–2025. The renewable-energy cube has 38 country categories and periods within 2004–2025; a country category does not guarantee any value cells. Individual country histories can be shorter, incomplete, or absent. In the home-selection import, both measures have observations for France, Italy, Poland, Portugal, and Spain. The checked responses contain no UK or Swiss observations for these selected datasets. These are partial-coverage snapshots, not a guarantee of future availability or a complete world ranking.

## Normalization and missing values

The API returns JSON-stat 2.0 cubes. The client validates dataset/source identity, annual frequency, unit, all selected dimensions, category indexes, cube size, country geography, and interval boundaries. It resolves flattened observation indexes from the declared dimension order, rather than assuming that geography and time always occupy the same positions. Both sparse objects and dense arrays are supported.

Numeric values use exact decimal arithmetic; zero and negative values are not treated as missing. Original observation status strings remain in `providerStatus`, including provisional (`p`), estimated (`e`), and break-in-series (`b`) flags and their combinations. Flags supply interpretation context independently of the application's `AVAILABLE`/`MISSING` status. An available value can still be provisional or estimated by the official source.

Only explicitly returned value cells or nonempty status cells create observations. A null value or a status-only cell becomes `MISSING`; a position absent from both collections creates no row. No older observation is carried forward, and absent years or unsupported countries are not filled with zero. Invalid cells, duplicate category positions, out-of-range indexes, changed definitions, and provider warning/error envelopes reject the response rather than exposing a partial import.

An unpublished annual interval can be a valid empty result. A real renewable-energy request for 2026 alone returned HTTP 200 with an empty time dimension and `value: {}`. The adapter accepts this as a successful empty history without manufacturing a current-year value.

The JSON-stat `updated` field and dataset annotations such as `UPDATE_DATA` and `UPDATE_STRUCTURE` describe dataset dissemination or maintenance. They do not establish the statistical revision date of each observation. Consequently, `sourceUpdatedAt` remains null, while `importedAt` independently records collection time. The original methodology and provider documentation identify how to interpret the selected periods and flags.

## Import and refresh

Import the country catalog first, then run the internal Eurostat command:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.eurostat-import.run-once=true
```

The importer uses the shared `atlas.import.countries`, `atlas.import.from-year`, and `atlas.import.to-year` settings. Their environment equivalents are `WORLD_BANK_COUNTRIES`, `WORLD_BANK_FROM_YEAR`, and `WORLD_BANK_TO_YEAR`. A zero end year means the current UTC year. Dataset availability determines the returned history; requesting earlier or later years does not create observations for them.

Override the scope for a bounded reconciliation:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.eurostat-import.run-once=true \
  --atlas.import.countries=FRA,ITA,POL,PRT,ESP,CHE \
  --atlas.import.from-year=2004 \
  --atlas.import.to-year=2025
```

Operational commands are mutually exclusive and suppress scheduled imports in their one-shot process. The command records an execution under source code `EUROSTAT`, exits after completion, and returns a nonzero exit code on failure. Source-specific PostgreSQL advisory locking prevents concurrent Eurostat imports. Shared transactional upserts retain unique country/series/year keys, exact values, flags, and collection timestamps. Repeated imports revise existing periods without duplicate rows; stored observations outside the requested scope are preserved. Import failures retain completed work and sanitized audit information.

Automatic additional-source refreshes are opt-in: set `ADDITIONAL_SOURCES_SCHEDULE_ENABLED=true`. The default `ADDITIONAL_SOURCES_IMPORT_CRON=0 0 5 * * SAT` runs at **05:00 UTC on Saturday** and refreshes the most recent six calendar years within the configured historical interval. It does not trigger an immediate initial import. Run the full historical command periodically to capture older source revisions. The database retains historical periods, not every vintage of a revised value.

There are no public write or administrative import endpoints. Histories, comparisons, rankings, and CSV exports use the existing public query API and its source-specific comparison rules.

## Validation

Recorded official fixtures cover debt, renewable-energy values and provisional flags, and the empty current-year time dimension. Contract tests verify source definitions, native precision, zero/negative/missing values, absent-cell handling, sparse and dense cubes, dimension-order changes, country-code exceptions, malformed responses, bounded requests, and transient HTTP retries. See the [fixture reference](../../backend/src/test/resources/eurostat/README.md) and the [validation report](../validation.md).
