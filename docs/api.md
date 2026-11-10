# Public API

Atlas Global exposes read-only JSON queries and CSV downloads under `/api/v1`, documented with OpenAPI 3.0. They query persisted PostgreSQL data without calling the World Bank or any other external provider. No account, token, session, or authentication header is required.

With the default Docker Compose configuration:

| Access | Address |
| --- | --- |
| API through the frontend proxy | `http://localhost:3000/api/v1` |
| API directly on the backend | `http://localhost:8080/api/v1` |
| Interactive Swagger UI | `http://localhost:3000/api/docs` or `http://localhost:8080/api/docs` |
| OpenAPI document | `http://localhost:3000/api/openapi` or `http://localhost:8080/api/openapi` |

The home page uses the persisted world overview and a curated country selection. The complete explorer at `/explorar`, profiles at `/paises/{iso3Code}`, comparison at `/comparar`, rankings at `/rankings`, and world map at `/mapa` use these public queries. The map loads geometry separately from a local static file; it does not obtain statistics from the geometry source. The same API can be used independently by clients and through Swagger UI.

## Pagination and ordering

Paginated collections have the following fields:

| Field | Meaning |
| --- | --- |
| `content` | Array of resource records on this page. |
| `page` | Zero-based page index. |
| `size` | Requested page size. |
| `totalElements` | Number of matching records across all pages. |
| `totalPages` | Number of pages at the requested size. |

Every paginated endpoint accepts these query parameters:

| Parameter | Default | Accepted values |
| --- | --- | --- |
| `page` | `0` | Integer from 0 through 10000. |
| `size` | `20` | Integer from 1 through 100. |
| `sort` | Depends on the resource | One of the fields in the table below. |
| `direction` | `asc` | `asc` or `desc`. |

| Collection | Default sort | Allowed sort fields | Stable tie breaker |
| --- | --- | --- | --- |
| Countries | `name` | `name`, `iso2Code`, `iso3Code`, `region`, `incomeLevel` | `iso3Code` |
| Sources | `name` | `name`, `code` | `code` |
| Indicators | `name` | `name`, `code`, `category` | `code` |
| Country indicator summaries | `name` | `name`, `code`, `category` | Indicator code, source code, and series ID |
| Historical observations | `period` | `period` | Country code |
| Ranking entries | `value` | `value` | Country code; equal values retain a shared rank |

Sort field names and `direction` values are case-sensitive. A valid page beyond the last result returns an empty `content` array with the matching total. Unknown sort fields and invalid page values return HTTP 400.

Search terms are case-insensitive. `%`, `_`, and `!` are treated as literal characters rather than SQL wildcard operators. Queries can be combined with the endpoint-specific filters below; applicable filters are joined with AND.

## Endpoints

| Method | Path | Result |
| --- | --- | --- |
| GET | `/overview` | Published world-aggregate highlights, with source-specific definitions and reference years. |
| GET | `/countries` | Paginated country/economy catalog. |
| GET | `/countries/{iso3Code}` | One country's public metadata. |
| GET | `/countries/{iso3Code}/indicators` | Paginated summaries, one entry per indicator series. |
| GET | `/countries/{iso3Code}/indicators/{indicatorCode}` | Paginated history for one country and one series. |
| GET | `/indicators` | Paginated indicator catalog. |
| GET | `/indicators/{indicatorCode}` | Indicator definition and available series. |
| GET | `/indicators/{indicatorCode}/history` | Paginated raw observations for one series and selected countries. |
| GET | `/indicators/{indicatorCode}/ranking` | One-year ranking with source definition, available years, and catalog coverage. |
| GET | `/exports/indicators` | Complete CSV download for one series, one interval, and one to five countries. |
| GET | `/policy-rates` | Latest dated policy rate and source-specific instrument for one to five countries. |
| GET | `/policy-rates/history` | Daily policy-rate observations for selected countries and an ISO date interval. |
| GET | `/policy-rates/export` | Complete dated policy-rate CSV with instrument and provenance. |
| GET | `/sources` | Paginated public source metadata. |
| GET | `/sources/{code}` | One source's public metadata. |

Country codes, indicator codes, and source codes are case-insensitive. Country paths require a three-character alphanumeric code. Indicator codes contain letters, digits, and dots; source codes contain letters, digits, and underscores. Invalid identifier formats return HTTP 400; a valid identifier that does not exist returns HTTP 404.

### Published world overview

`GET /overview` returns a non-paginated object:

| Field | Meaning |
| --- | --- |
| `scope` | `{code: "WLD", name: "Mundo"}` identifies the World Bank world aggregate. |
| `indicators` | Array of the stored world highlights: GDP, population, life expectancy, and Internet use. |

Each entry in `indicators` contains `indicator`, `series`, `latestAvailable`, and `latestReported`. The first two use the same definition and source schemas described below. World observations contain `period`, `value`, `status`, `providerStatus`, `sourceUpdatedAt`, and `importedAt`; they have no country code because `scope` identifies their aggregate.

`latestAvailable` is the latest numeric observation, or null. `latestReported` is the latest imported observation, including an explicit missing value, or null. Their years may differ, and reference years can differ between indicators. Before catalog import, `indicators` can be empty; a known series without world observations has null latest-value fields.

These figures are the provider's published `WLD` observations, stored separately from country data. The endpoint does not sum or average the selected countries. `WLD` is not inserted into the country catalog and cannot be selected as a country in historical comparisons. This query reads PostgreSQL without refreshing the provider.

```bash
curl --fail 'http://localhost:3000/api/v1/overview'
```

### Countries

`GET /countries` accepts:

| Filter | Behavior |
| --- | --- |
| `q` | Matches localized name, original name, ISO2, ISO3, or region; maximum 100 characters. |
| `region` | Exact region name, ignoring case and surrounding whitespace; maximum 200 characters. |
| `incomeLevel` | Exact income-level name, ignoring case and surrounding whitespace; maximum 200 characters. |

Region and income filters use the names stored in the catalog, not external provider classification codes. Country metadata includes `id`, `iso2Code`, `iso3Code`, `name`, `originalName`, `region`, `incomeLevel`, `capital`, `latitude`, and `longitude`. Optional metadata can be null. The catalog includes eligible World Bank countries/economies and a Taiwan identity retained from the official IMF country catalog. Optional Taiwan metadata and observations are not inferred. Regional/world aggregates are excluded. The home page's 25-location selection is a frontend navigation choice and does not restrict this complete catalog.

```bash
curl --fail 'http://localhost:3000/api/v1/countries?q=Brasil&size=10'
curl --fail 'http://localhost:3000/api/v1/countries/BRA'
```

### Indicators and series

`GET /indicators` accepts:

| Filter | Behavior |
| --- | --- |
| `q` | Matches indicator name or code; maximum 100 characters. |
| `category` | One of `ECONOMY`, `DEMOGRAPHY`, `EDUCATION`, `HEALTH`, `ENVIRONMENT`, `TECHNOLOGY`, `INEQUALITY`, `SAFETY`, or `MIGRATION`. |
| `source` | Source code, such as `WORLD_BANK`, `OECD`, `IBGE`, or `BCB`. |

Category values are uppercase and case-sensitive. A supported category may have no imported indicators. Unknown filter matches return an empty collection; an invalid category value returns HTTP 400.

Each catalog record has `id`, `code`, `name`, `description`, and `category`.

`GET /indicators/{indicatorCode}` returns an object with:

- `indicator`: the catalog record.
- `series`: available source-specific series.

Each series has `id`, `externalCode`, `datasetCode`, `originalName`, `methodology`, `sourceOrganization`, `unit`, `frequency`, `source`, and `comparison`. The nested source includes `id`, `code`, `name`, `baseUrl`, `description`, and `documentationUrl`. Unit values are machine-readable identifiers; the indicator and methodology supply their context. No unit conversion is performed by query endpoints. OECD wage units include the constant-price PPP base, for example `USD_PPP_2025_ANNUAL`; the dataset identity also includes that base. A different base is a separate series, not a conversion performed during the query.

Brazilian national definitions also retain distinct identities:

| Indicator code | Source | Unit and reference |
| --- | --- | --- |
| `IBGE.GINI.HOUSEHOLD.PER.CAPITA` | `IBGE` | `GINI_COEFFICIENT`, original 0–1 scale. |
| `IBGE.HOUSEHOLD.INCOME.PER.CAPITA` | `IBGE` | `BRL_REAL_{baseYear}_MONTHLY_PER_PERSON`, monthly household income per person at the table's published constant-price base. |
| `BCB.HOUSEHOLD.DEBT.DECEMBER` | `BCB` | `PERCENT_HOUSEHOLD_RNDBF_12M`, actual December household-debt ratio to restricted RNDBF accumulated over twelve months. |

The IBGE real-income dataset identity includes its price base, such as `SIDRA_7533:2025`; rebased values form a separate series. No conversion joins the national Gini coefficient to the WDI 0–100 index, income in reais to OECD PPP wages, or BCB restricted RNDBF debt to OECD net-disposable-income debt. See the [Brazilian adapter reference](integrations/brazil.md).

`comparison` contains:

| Field | Meaning |
| --- | --- |
| `allowed` | Whether this series permits cross-country histories, rankings, and multi-country exports. |
| `note` | Public methodological context, currently in Brazilian Portuguese, for display alongside the series. |

National poverty lines (`SI.POV.NAHC` from `WORLD_BANK`) have `allowed: false`. Their country-specific thresholds preclude direct country comparisons, while individual country histories remain available. IBGE and BCB national definitions also have `allowed: false` because their coverage is Brazil, not a harmonized multi-country dataset. Other notes identify limitations in survey concepts, coverage, price bases, or statistical definitions. `allowed: true` permits exploration within one series; it does not assert perfect harmonization or a complete ranking. See [data coverage and comparability](data-coverage.md).

```bash
curl --fail 'http://localhost:3000/api/v1/indicators?category=ECONOMY&source=WORLD_BANK&sort=code'
curl --fail 'http://localhost:3000/api/v1/indicators/NY.GDP.MKTP.CD'
```

### Country indicator summaries

`GET /countries/{iso3Code}/indicators` accepts the same `category` and `source` filters as the indicator catalog, plus pagination and ordering. It returns one entry per matching series, rather than merging definitions from different sources.

Each entry has:

| Field | Meaning |
| --- | --- |
| `indicator` | Indicator definition. |
| `series` | Series definition, unit, methodology, and source. |
| `latestAvailable` | Most recent observation with a numeric value, or null if none exists. |
| `latestReported` | Most recent observation received, including a `MISSING` observation, or null if none exists. |

The year of `latestAvailable` may precede `latestReported` when the provider's most recent record is missing. A country may exist in the catalog without observations for a series; both fields are then null. An unknown country returns HTTP 404.

IBGE and BCB definitions appear in country summaries only for `BRA`. This coverage filter applies to both pagination counts and returned rows. The global indicator catalog still exposes their definitions. International series without observations retain their null latest-value fields; national scope filtering does not convert missing international data into an absent definition.

```bash
curl --fail 'http://localhost:3000/api/v1/countries/BRA/indicators?category=ECONOMY&sort=code'
```

### Historical observations

Both history endpoints accept:

| Parameter | Behavior |
| --- | --- |
| `from` | Inclusive first year; default 1960. |
| `to` | Inclusive last year; defaults to the current UTC year. |
| `seriesId` | UUID of a series returned by the indicator detail endpoint. |
| `page`, `size`, `sort`, `direction` | Pagination and ordering as documented above. |

Years must be between 1800 and 2200, and `from` must not exceed `to`. No values are synthesized for years that were not returned by the provider.

For `/indicators/{indicatorCode}/history`, `countries` is required: provide one to five distinct three-character country codes separated by commas, for example `BRA,USA`. Duplicates, malformed codes, and more than five codes return HTTP 400. A well-formed country code absent from the catalog returns HTTP 404.

The response contains:

| Field | Meaning |
| --- | --- |
| `indicator` | Indicator definition. |
| `series` | The selected definition, including unit and source metadata. |
| `countries` | Country references with `iso3Code` and `name`. |
| `from`, `to` | Effective requested interval. |
| `observations` | Paginated observation records. |

Each observation has `countryCode`, `period`, `value`, `status`, `providerStatus`, `sourceUpdatedAt`, and `importedAt`. `period` is an annual integer. For `BCB.HOUSEHOLD.DEBT.DECEMBER`, it identifies the year of the actual December observation, not an average of the year's months. The importer excludes incomplete calendar years. IBGE income remains an annual survey statistic expressed as a monthly per-person amount.

`sourceUpdatedAt` is a validated statistical update date supplied by the provider, or null when unavailable; `importedAt` is the collection timestamp. HTTP resource and catalog-maintenance dates do not establish a statistical revision date and are not substituted for this field. IBGE and BCB observations therefore keep unknown revision dates null while retaining their collection timestamps.

`value` preserves the source's decimal value. A numeric zero is an `AVAILABLE` observation. A provider null is `MISSING` with `value: null`. The absence of an observation record is distinct from an explicit `MISSING` record. A known country/series with no matching records returns a successful response with an empty observation page.

IBGE's published `-` symbol means absolute zero and becomes `AVAILABLE` with numeric zero. The published `..`, `...`, and `X` symbols become `MISSING`; the original symbol remains in `providerStatus`. An explicit single-country historical request for an IBGE/BCB definition outside Brazil returns an empty observation collection, without generating national values for another country.

If an indicator has more than one series, omitting `seriesId` returns HTTP 409. Retrieve `/indicators/{indicatorCode}` and use the desired series UUID. An unknown UUID or a UUID belonging to another indicator returns HTTP 404; a malformed UUID returns HTTP 400. This prevents silent mixing of units, methodologies, or sources.

A request with more than one country also checks `series.comparison.allowed`. A blocked definition, including national poverty lines and IBGE/BCB national definitions, returns HTTP 409 with a public methodological or coverage explanation. This restriction applies even when an explicit `seriesId` is supplied. A one-country request for that definition remains valid.

Multi-country history returns raw observations from the same selected series. It does not align periods, fill gaps, interpolate values, or calculate a country comparison. The web comparison view collects all pages, preserves per-country chart gaps, and derives its summary from the latest year in the requested interval with numeric values for every selected country. When no such year exists, it reports unavailable common-year values rather than mixing reference years.

```bash
curl --fail 'http://localhost:3000/api/v1/countries/BRA/indicators/NY.GDP.MKTP.CD?from=2020&to=2024'
curl --fail 'http://localhost:3000/api/v1/indicators/NY.GDP.MKTP.CD/history?countries=BRA,USA&from=2020&to=2024&size=100&direction=desc'
```

### Sources

`GET /sources` accepts `q` to search source names or codes, with a maximum of 100 characters. `GET /sources/{code}` retrieves a source directly. These endpoints expose only public provenance metadata, not import history, operational errors, or credentials.

```bash
curl --fail 'http://localhost:3000/api/v1/sources?q=World%20Bank'
curl --fail 'http://localhost:3000/api/v1/sources/WORLD_BANK'
```

## Rankings

`GET /indicators/{indicatorCode}/ranking` accepts `seriesId`, optional `year` (1800–2200), and standard pagination. Only `sort=value` is supported. The API defaults to ascending order; the web interface explicitly requests descending order initially. If `year` is omitted, the most recent year with at least one numeric country observation is selected. An ambiguous indicator requires `seriesId` (409), and a definition with country comparison disabled also returns 409.

The response contains `indicator`, `series`, `year`, `availableYears` (descending), `coverage`, and a paginated `ranking`. Coverage reports `totalCountries` (the complete persisted country/economy catalog), `withData` (numeric observations for the selected series/year), and `withoutData` (their difference). It does not claim complete worldwide statistical coverage. World aggregate observations are excluded. With no numeric history, the default `year` is null and the ranking is empty; a valid requested year with no observations also returns an empty ranking.

Each entry has `rank`, `country` (`iso3Code`, `name`), exact decimal `value`, and nullable `providerStatus`. Only numeric observations from that one year enter the ranking. Zero and negative values remain valid; missing countries are excluded from the entries and counted in coverage. Equal values share competition ranks, for example 1, 1, 3. Country codes stabilize the display order of tied rows. Positions are calculated before pagination. Definition, coverage, and entries share a repeatable-read snapshot. Native classifications remain visible: an IMF projection can enter a selected-year ranking, but is identified explicitly rather than presented as a final observed value.

```bash
curl --fail 'http://localhost:3000/api/v1/indicators/SP.POP.TOTL/ranking?year=2024&direction=desc&size=20'
```

## CSV export

`GET /exports/indicators` requires `indicator` and one to five distinct `countries`, and accepts `seriesId`, `from` (default 1960), and `to` (current UTC year by default). Annual history validation and series-selection rules apply. One-country national exports are available; multi-country exports enforce the comparison policy. Invalid or blocked requests return a problem response before any attachment bytes are sent.

The export collects all matching history pages within a read-only repeatable-read transaction. It returns `text/csv;charset=UTF-8;header=present`, attachment disposition, UTF-8 BOM, comma-separated fields, and CRLF records. Column names are:

```text
country_code,country,indicator_code,indicator,period,value,unit,source_code,source,series_id,dataset_code,status,provider_status,source_updated_at,collected_at
```

`period` is an integer year. Numeric cells use exact decimal notation, without localized separators or rounding; negative numbers remain numeric. Missing observations have an empty value and explicit `MISSING` status. A year without an imported record generates no CSV row. Unknown statistical revision dates are empty, while collection timestamps remain independent.

Text cells use RFC 4180 quoting and doubled embedded quotes. Text beginning with spreadsheet formula markers or control characters receives a leading tab to reduce formula interpretation. This is an intentional text transformation, not a universal guarantee for every spreadsheet application; use JSON when exact unmodified provider text is required. Decimal values are never prefixed. A valid selection with no observations downloads headers without manufactured rows.

```bash
curl --fail --output atlas-global.csv 'http://localhost:3000/api/v1/exports/indicators?indicator=SP.POP.TOTL&countries=BRA,USA&from=2020&to=2024'
```

## Additional annual sources

IMF, UNDP, and Eurostat use the existing public indicator, country-summary, history, comparison, ranking, and CSV endpoints. No provider-specific public import route is added. Select one exact series UUID; annual frequency does not make different sources, report editions, or national definitions interchangeable.

| Source | Indicator codes | Unit and dataset identity |
| --- | --- | --- |
| `IMF` | `IMF.GOVERNMENT.DEBT.GDP`, `IMF.CURRENT.ACCOUNT.GDP` | `PERCENT_GDP`; `WEO:{YYYY-MM}` identifies the publication edition. |
| `UNDP` | `UNDP.HDI`, `UNDP.IHDI` | `INDEX_0_1`; `HDR:{YYYY}` identifies the report edition. |
| `EUROSTAT` | `EUROSTAT.GOVERNMENT.DEBT.GDP`, `EUROSTAT.RENEWABLE.ENERGY.SHARE` | `PERCENT_GDP` / `PERCENT_GROSS_FINAL_ENERGY`; datasets `GOV_10DD_EDPT1` / `SDG_07_40`. |

`status: AVAILABLE` means a numeric value exists; it does not assert a final observed statistic. IMF `providerStatus: IMF_PROJECTION` marks years at or after the native projection boundary; `IMF_HISTORICAL` marks earlier WEO values, which may also include staff estimates. These classifications accompany histories, latest summaries, ranking entries, and CSV rows. Eurostat retains native flags such as `p` (provisional), `e` (estimated), and `b` (break in series). UNDP supplies no validated per-cell flags, so its `providerStatus` remains null.

Original decimal values, reference years, source organizations, and methodologies are preserved. A report edition or dataset update is not treated as the revision date of every observation; unknown `sourceUpdatedAt` values remain null. Missing countries and absent years are not manufactured. Taiwan has native IMF observations when imported explicitly, without acquiring invented WDI or UNDP coverage.

```bash
curl --fail 'http://localhost:3000/api/v1/indicators?source=IMF'
curl --fail 'http://localhost:3000/api/v1/countries/BRA/indicators/UNDP.HDI?from=2020&to=2023'
curl --fail 'http://localhost:3000/api/v1/indicators/EUROSTAT.GOVERNMENT.DEBT.GDP/ranking?year=2025&direction=desc'
```

See the [IMF](integrations/imf.md), [UNDP](integrations/un-development.md), and [Eurostat](integrations/eurostat.md) references for exact upstream contracts and coverage limitations. Multiple imported publication editions require an explicit `seriesId`, just as any other multi-series indicator does.

## Policy rates

The dated measure `POLICY.RATE` is served separately from the annual indicator catalog. Its unit is `PERCENT_PER_ANNUM`: percent per year describes the rate, while observation frequency remains `DAILY`. Annual history and ranking endpoints retain their existing integer-year contracts.

All three policy-rate endpoints require `countries`: one to five distinct ISO3 codes, comma-separated. Invalid selections return 400; an unknown country returns 404. A known country without an integrated rate returns 200 with null `series`/`latestAvailable` and an `unavailableReason`, rather than a fabricated zero.

`GET /policy-rates` returns `code`, `name`, `unit`, `asOf` (current UTC date), `countries`, null `from`/`to`, and an empty `observations` array. Each country entry contains:

| Field | Meaning |
| --- | --- |
| `country` | ISO3 code and localized name. |
| `series` | ID, external code, frequency, instrument, methodology, source organization, scope note, and public source metadata; null without an integrated definition. |
| `latestAvailable` | Latest numeric observation through `asOf`, retaining its own reference date; null if unavailable. |
| `unavailableReason` | Public coverage or discontinuation notice; it can accompany a historical numeric value that is no longer a current policy instrument. |

`GET /policy-rates/history` accepts optional inclusive ISO dates `from` and `to`. `to` defaults to today in UTC; `from` defaults to one year before `to`. Dates must be valid, no earlier than 1900, no later than today, ordered, and at most twenty years apart. The bounded response contains all matching rows without pagination, ordered by date and country. `from` and `to` contain the effective interval; country summaries still identify each country's latest available value independently of that interval.

Each observation has `countryCode`, `date` (`YYYY-MM-DD`), exact decimal `value`, `status`, `providerStatus`, `sourceUpdatedAt`, and `importedAt`. Zero and negative rates are valid. Explicit missing values have `status: MISSING` and null `value`; an absent source date creates no observation. Unknown statistical revision dates remain null, independently of collection timestamps. No daily gap filling, interpolation, annual averaging, currency adjustment, or substitution of a different instrument occurs.

Cross-country results identify each source and instrument independently. They permit exploration without asserting that a target, repo rate, and target-band midpoint are methodologically identical. Euro-area mappings identify a shared monetary instrument explicitly. See the [policy-rate integration reference](integrations/policy-rates.md).

`GET /policy-rates/export` accepts the same country/date parameters and returns UTF-8 CSV with BOM, comma separators, CRLF records, exact decimal values, and attachment disposition. Its columns are:

```text
country_code,country,indicator,period,value,unit,frequency,source_code,source,instrument,external_code,status,provider_status,source_updated_at,collected_at
```

`period` retains the full ISO date. Text escaping follows the annual [CSV export](#csv-export) rules. A read-only repeatable-read transaction keeps metadata and rows consistent within a response. It does not query an external source or initiate a refresh.

```bash
curl --fail 'http://localhost:3000/api/v1/policy-rates?countries=BRA,USA,UKR'
curl --fail 'http://localhost:3000/api/v1/policy-rates/history?countries=BRA,USA&from=2026-01-01&to=2026-11-10'
curl --fail --output policy-rates.csv 'http://localhost:3000/api/v1/policy-rates/export?countries=BRA,USA&from=2026-01-01&to=2026-11-10'
```

## Errors and access

Errors use HTTP status codes with `application/problem+json` responses. Problem details identify the status and describe the failure without exposing internal stack traces or database credentials.

| Status | Meaning |
| --- | --- |
| 400 | Invalid filter, code format, pagination, UUID, interval, or country selection. |
| 404 | Requested resource does not exist. |
| 405 | A write method was used on a read-only endpoint. |
| 409 | An ambiguous indicator requires `seriesId`, or a multi-country request uses a definition with direct comparison disabled. |
| 429 | The public Nginx entry point rejected an API/download burst; retry after the indicated delay. |
| 500 | Unexpected server failure with a generic public message. |

There are no public endpoints for imports, jobs, administration, accounts, or authentication. Import commands remain operational processes outside the query API. API requests do not trigger refreshes or fetch data from external providers. Actuator health endpoints use a separate restricted management port, as described in the root README.

In the self-hosted Compose deployment, the frontend proxy limits reads to ten requests per second per connection/client address with a burst of 100; CSV paths additionally allow two per second with a burst of five. A limited request returns 429 and `Retry-After: 1`. Configure exact trusted ingress addresses before relying on forwarded client IPs; arbitrary forwarded headers are ignored by default. The backend's container/private address is an operational interface rather than a second public entry point.

In the free Pages/Render deployment, clients use the Render HTTPS origin directly, including JSON, CSV, `/api/docs`, and `/api/openapi`. Pages does not proxy `/api`. CORS accepts only the exact production Pages origin, without credentials. The Nginx traffic limits are absent on this route. Public `/healthz` reports only application availability; private Actuator readiness remains the database check. See [deployment](deployment.md).

Small catalog and overview queries use a bounded sixty-second application cache. Import-audit changes invalidate keys across processes; active snapshot transactions bypass cached catalog entries to preserve their database view. Histories, rankings, and CSV bodies remain uncached. Compression and static browser-cache behavior are handled by Nginx; HTML is revalidated, while hashed bundles retain long-lived caching. See the [security reference](security.md).
