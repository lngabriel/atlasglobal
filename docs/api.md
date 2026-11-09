# Public API

Atlas Global exposes read-only JSON endpoints under `/api/v1`, documented with OpenAPI 3.0. They query persisted PostgreSQL data without calling the World Bank or any other external provider. No account, token, session, or authentication header is required.

With the default Docker Compose configuration:

| Access | Address |
| --- | --- |
| API through the frontend proxy | `http://localhost:3000/api/v1` |
| API directly on the backend | `http://localhost:8080/api/v1` |
| Interactive Swagger UI | `http://localhost:3000/api/docs` or `http://localhost:8080/api/docs` |
| OpenAPI document | `http://localhost:3000/api/openapi` or `http://localhost:8080/api/openapi` |

The web application does not yet display analytical queries. The endpoints below can be used directly by clients and through Swagger UI.

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

### Countries

`GET /countries` accepts:

| Filter | Behavior |
| --- | --- |
| `q` | Matches localized name, original name, ISO2, ISO3, or region; maximum 100 characters. |
| `region` | Exact region name, ignoring case and surrounding whitespace; maximum 200 characters. |
| `incomeLevel` | Exact income-level name, ignoring case and surrounding whitespace; maximum 200 characters. |

Region and income filters use the names stored in the catalog, not external provider classification codes. Country metadata includes `id`, `iso2Code`, `iso3Code`, `name`, `originalName`, `region`, `incomeLevel`, `capital`, `latitude`, and `longitude`. Optional metadata can be null. The catalog includes World Bank countries/economies and excludes regional aggregates.

```bash
curl --fail 'http://localhost:3000/api/v1/countries?q=Brasil&size=10'
curl --fail 'http://localhost:3000/api/v1/countries/BRA'
```

### Indicators and series

`GET /indicators` accepts:

| Filter | Behavior |
| --- | --- |
| `q` | Matches indicator name or code; maximum 100 characters. |
| `category` | One of `ECONOMY`, `DEMOGRAPHY`, `EDUCATION`, `HEALTH`, `ENVIRONMENT`, or `TECHNOLOGY`. |
| `source` | Source code, such as `WORLD_BANK`. |

Category values are uppercase and case-sensitive. A supported category may have no imported indicators. Unknown filter matches return an empty collection; an invalid category value returns HTTP 400.

Each catalog record has `id`, `code`, `name`, `description`, and `category`.

`GET /indicators/{indicatorCode}` returns an object with:

- `indicator`: the catalog record.
- `series`: available source-specific series.

Each series has `id`, `externalCode`, `datasetCode`, `originalName`, `methodology`, `sourceOrganization`, `unit`, `frequency`, and `source`. The nested source includes `id`, `code`, `name`, `baseUrl`, `description`, and `documentationUrl`. Unit values are machine-readable identifiers; the indicator and methodology supply their context. No unit conversion is performed by query endpoints.

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

Each observation has `countryCode`, `period`, `value`, `status`, `providerStatus`, `sourceUpdatedAt`, and `importedAt`. `period` is an annual integer. `sourceUpdatedAt` is the update date supplied by the provider; `importedAt` is the collection timestamp.

`value` preserves the source's decimal value. A numeric zero is an `AVAILABLE` observation. A provider null is `MISSING` with `value: null`. The absence of an observation record is distinct from an explicit `MISSING` record. A known country/series with no matching records returns a successful response with an empty observation page.

If an indicator has more than one series, omitting `seriesId` returns HTTP 409. Retrieve `/indicators/{indicatorCode}` and use the desired series UUID. An unknown UUID or a UUID belonging to another indicator returns HTTP 404; a malformed UUID returns HTTP 400. This prevents silent mixing of units, methodologies, or sources.

Multi-country history returns raw observations from the same selected series. It does not align periods, fill gaps, interpolate values, or calculate a country comparison.

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
| 409 | The requested indicator has multiple series and requires `seriesId`. |
| 500 | Unexpected server failure with a generic public message. |

There are no public endpoints for imports, jobs, administration, accounts, or authentication. Import commands remain operational processes outside the query API. API requests do not trigger refreshes or fetch data from external providers. Actuator health endpoints use a separate restricted management port, as described in the root README.
