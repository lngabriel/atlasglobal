# Application architecture

Atlas Global uses a modular Spring Boot monolith and a separate React frontend. PostgreSQL stores the country catalog, indicator definitions, source-specific annual series, observations, and import history. No accounts, authentication, or user-related tables exist.

## Backend boundaries

- `country`, `indicator`, `observation`, and `source` contain domain mappings and the public query controllers, services, repositories, and response DTOs.
- `ingestion` defines normalized provider records, transactional storage, import coordination, an operational command, and optional scheduling.
- `integration/worldbank` owns HTTP configuration, provider-specific DTOs, pagination, retries, metadata validation, and conversion into internal records.
- `shared` contains explicit CORS configuration, bounded pagination, safe MVC errors, and OpenAPI configuration.

External response DTOs do not cross the provider boundary. JPA mappings are validated against the Flyway-managed schema. The importer uses parameterized PostgreSQL batch upserts for efficient and idempotent persistence.

Country, indicator, and source catalogs use parameterized Spring Data JPA queries. Historical and latest-value projections use parameterized JDBC queries inside read-only service transactions. Responses preserve a single series definition, source, and unit; ambiguous histories require an explicit series ID. Query requests never call external providers.

Read the [API reference](../api.md), [data model](../data-model.md) for schema constraints and [World Bank adapter documentation](../integrations/world-bank.md) for provider behavior.

## Runtime

Docker Compose runs PostgreSQL, the backend, and an unprivileged Nginx frontend. Database health gates backend startup. Nginx serves the static React build with SPA routing and forwards `/api` to the backend. Vite provides an equivalent proxy during local development.

Published Compose ports bind to localhost. Actuator uses a separate loopback interface and port with only health exposed; the port is not published or proxied. Readiness checks the database. CORS allows exact configured origins and read methods without browser credentials.

The import command runs internally and exits after completion. Scheduled imports are opt-in. PostgreSQL advisory locking prevents overlapping imports across instances. Normal application startup performs no upstream requests unless scheduling or the operational command is explicitly enabled.

## Frontend

React Router serves the country explorer at `/`, standalone country profiles at `/paises/:iso3Code`, and project information at `/sobre`. The explorer opens with Brazil selected and provides quick country links for Brazil, the United States, and China, using names from the API catalog. Its paginated search supports country names, ISO codes, regions, and income groups. Search filters, country selection, indicator category, indicator, series, and interval are stored in URL parameters. Only the display theme is stored in localStorage.

The UI uses Tailwind CSS and local shadcn/ui components with a monochrome light/dark palette. A shared country dashboard presents four headline indicators, a source-specific indicator catalog with category filtering, and one selected historical chart. Recharts 3 renders the chart in a lazily loaded bundle. A keyboard-accessible table provides the same annual data, and source metadata remains available next to the visualization. Country metadata, summaries, and histories come from the existing public API; no synthetic production figures are included.

`services/api.ts` owns the typed HTTP contracts, safe localized errors, request timeouts, and complete collection retrieval. `hooks/use-atlas-data.ts` integrates those requests with TanStack Query using country, series, and interval in cache keys. Obsolete requests are canceled; permanent client errors are not retried. Histories explicitly select a source-specific series and collect all API pages before presenting the interval. A failed or inconsistent page rejects the result instead of displaying partial history. Search results remain paginated; a separately cached country catalog supplies region and income filter options.

Presentation transformations in `lib/` format values and units in Brazilian Portuguese and preserve the distinction between zero, an explicit missing observation, and a year without an imported record. Missing years receive null chart placeholders without creating source observations, so lines never bridge gaps. The history table labels both kinds of absence separately. Annual changes require numeric observations from consecutive years in the same series: percentage units use percentage points, life expectancy uses years, and other units use a relative percentage when the previous value is nonzero. Each indicator retains its own latest available year.

The default chart interval covers 20 calendar years through the current UTC year. Presets select 10 years, 20 years, or the period since 1960; custom intervals are validated before querying. Series definitions, units, source organizations, source update dates, and collection dates remain visible or available through the definition disclosure. Loading, empty, and failure states avoid presenting unavailable data as zero.

## Configuration and operations

Database credentials are environment variables and never reach the browser. Import scope and scheduling are configured through environment variables or command arguments. No public administrative or import endpoints exist. Logs use structured JSON; persisted errors omit internal stack traces and provider payloads.

Backend Docker builds use BuildKit cache mounts for Maven project dependencies and compile the application with unit tests. Frontend Docker builds run `npm test` before `npm run build`. Browser checks run separately against the running application and imported dataset. Swagger UI is available through the frontend proxy, with an inline-style exception scoped to its own route while the application keeps its stricter policy.

The Compose configuration is intended for local development. Deployment-specific TLS, network policy, and database roles are operational concerns outside this configuration.
