# Public API

Atlas Global exposes read-only JSON endpoints under `/api/v1`, documented with OpenAPI 3.0. They query persisted PostgreSQL data without calling the World Bank or any other external provider. No account, token, session, or authentication header is required.

With the default Docker Compose configuration:

| Access | Address |
| --- | --- |
| API through the frontend proxy | `http://localhost:3000/api/v1` |
| API directly on the backend | `http://localhost:8080/api/v1` |
| Interactive Swagger UI | `http://localhost:3000/api/docs` or `http://localhost:8080/api/docs` |
| OpenAPI document | `http://localhost:3000/api/openapi` or `http://localhost:8080/api/openapi` |

The home page uses the persisted world overview and a curated country selection. The complete explorer at `/explorar`, profiles at `/paises/{iso3Code}`, and comparison at `/comparar` use the catalogs and source-specific historical endpoints. The same API can be used independently by clients and through Swagger UI.

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
| `source` | Source code, such as `WORLD_BANK` or `OECD`. |

Category values are uppercase and case-sensitive. A supported category may have no imported indicators. Unknown filter matches return an empty collection; an invalid category value returns HTTP 400.

Each catalog record has `id`, `code`, `name`, `description`, and `category`.

`GET /indicators/{indicatorCode}` returns an object with:

- `indicator`: the catalog record.
- `series`: available source-specific series.

Each series has `id`, `externalCode`, `datasetCode`, `originalName`, `methodology`, `sourceOrganization`, `unit`, `frequency`, `source`, and `comparison`. The nested source includes `id`, `code`, `name`, `baseUrl`, `description`, and `documentationUrl`. Unit values are machine-readable identifiers; the indicator and methodology supply their context. No unit conversion is performed by query endpoints. OECD wage units include the constant-price PPP base, for example `USD_PPP_2025_ANNUAL`; the dataset identity also includes that base. A different base is a separate series, not a conversion performed during the query.

`comparison` contains:

| Field | Meaning |
| --- | --- |
| `allowed` | Whether this series permits a multi-country historical request. |
| `note` | Public methodological context, currently in Brazilian Portuguese, for display alongside the series. |

National poverty lines (`SI.POV.NAHC` from `WORLD_BANK`) have `allowed: false`. Their country-specific thresholds preclude direct country comparisons, while individual country histories remain available. Other notes identify limitations in survey concepts, coverage, price bases, or statistical definitions. `allowed: true` permits exploration within one series; it does not assert perfect harmonization or a complete ranking. See [data coverage and comparability](data-coverage.md).

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

Each observation has `countryCode`, `period`, `value`, `status`, `providerStatus`, `sourceUpdatedAt`, and `importedAt`. `period` is an annual integer. `sourceUpdatedAt` is a validated statistical update date supplied by the provider, or null when unavailable; `importedAt` is the collection timestamp. OECD transport-header dates do not establish a statistical revision date and are not substituted for this field.

`value` preserves the source's decimal value. A numeric zero is an `AVAILABLE` observation. A provider null is `MISSING` with `value: null`. The absence of an observation record is distinct from an explicit `MISSING` record. A known country/series with no matching records returns a successful response with an empty observation page.

If an indicator has more than one series, omitting `seriesId` returns HTTP 409. Retrieve `/indicators/{indicatorCode}` and use the desired series UUID. An unknown UUID or a UUID belonging to another indicator returns HTTP 404; a malformed UUID returns HTTP 400. This prevents silent mixing of units, methodologies, or sources.

A request with more than one country also checks `series.comparison.allowed`. A blocked definition, including national poverty lines, returns HTTP 409 with a public methodological explanation. This restriction applies even when an explicit `seriesId` is supplied. A one-country request for that definition remains valid.

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

## Errors and access

Errors use HTTP status codes with `application/problem+json` responses. Problem details identify the status and describe the failure without exposing internal stack traces or database credentials.

| Status | Meaning |
| --- | --- |
| 400 | Invalid filter, code format, pagination, UUID, interval, or country selection. |
| 404 | Requested resource does not exist. |
| 405 | A write method was used on a read-only endpoint. |
| 409 | An ambiguous indicator requires `seriesId`, or a multi-country request uses a definition with direct comparison disabled. |
| 500 | Unexpected server failure with a generic public message. |

There are no public endpoints for imports, jobs, administration, accounts, or authentication. Import commands remain operational processes outside the query API. API requests do not trigger refreshes or fetch data from external providers. Actuator health endpoints use a separate restricted management port, as described in the root README.
