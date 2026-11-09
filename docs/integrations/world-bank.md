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

## Resilience

Defaults: 500 rows per page, 10-second connection timeout, 45-second read timeout, and at most three attempts. Retries apply to connection failures, HTTP 429, and HTTP 5xx. Permanent HTTP failures, malformed JSON, provider error envelopes, incomplete results, and incompatible identifiers fail immediately.

Backoff is bounded. `Retry-After` supports seconds and HTTP dates; waits beyond one minute fail the current command for an operational retry later. Interrupted waits restore the thread interrupt flag. Source payloads and stack traces are not placed in persisted error messages.

## Operations

The import command and optional Spring scheduler call the same service. No import controller or administrative route exists. Startup without the import flag and without scheduling performs no provider calls.

The default catalog imports eight indicators for nine validation countries, while importing all nonaggregate country metadata. The configured country list can include any eligible country code returned by World Bank, without code changes. Imports accept 1–50 distinct country codes per execution.

Manual execution uses the configured historical interval. The weekly scheduler refreshes the most recent six-year window within that interval. Full historical reconciliations use the same manual command. Scheduled execution is disabled by default and is always suppressed in one-shot import processes.

Use the commands in the root README to inspect import status and coverage. Do not interpret the imported subset as a complete world ranking.

## Validation

On 2026-11-06, a real historical import into an isolated PostgreSQL 17 database stored 217 nonaggregate countries, eight indicator definitions, and 4,752 observations for the nine configured countries. Of those observations, 4,007 had numeric values and 745 were explicitly missing. These counts describe that provider snapshot, not a permanent coverage guarantee.

Test fixtures are derived from real responses captured on that date. The country fixture is shortened to Brazil, the United States, and the World aggregate, with its total adjusted for the test. Controlled unit/error/pagination fixtures exist only in tests; they are never loaded by the application.

The full historical import was repeated successfully: the database still contained 4,752 unique country/series/year observations. A persisted Brazil GDP observation for 2025 matched the official response value exactly (`2279920092492.13`).

Automated test results are recorded in the [validation report](../validation.md). Backend and frontend Docker images built successfully, and an isolated three-service Compose deployment returned readiness UP. The documented container import command completed a separate 2022–2024 import with 216 observations. The frontend served its SPA fallback route through Nginx.

A command requesting an unavailable country returned exit code 1 and persisted a FAILED import with zero processed records, while the previous successful import remained intact.
