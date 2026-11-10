# Application architecture

Atlas Global uses a modular Spring Boot monolith and a separate React frontend. PostgreSQL stores country identities, indicator definitions, source-specific annual series, country and world observations, and import history. Access is public; no accounts, authentication, or user-related tables exist.

## Backend boundaries

- `country`, `indicator`, `observation`, and `source` contain domain mappings and public query controllers, services, repositories, and response DTOs.
- `overview` queries the World Bank's published world aggregates independently of the country catalog.
- `ingestion` defines normalized provider records, transactional storage, provider import coordination, internal commands, and optional World Bank scheduling.
- `integration/worldbank` owns HTTP configuration, provider DTOs, pagination, retries, metadata validation, and normalization for 24 WDI definitions.
- `integration/oecd` owns bounded annual CSV decoding, explicit dimension selection, price-base handling, retries, and imports for three OECD definitions.
- `shared` contains explicit CORS configuration, bounded pagination, safe MVC errors, and OpenAPI configuration.

External response DTOs do not cross provider boundaries. Provider-independent source and series definitions feed parameterized PostgreSQL upserts. Flyway owns the schema; Hibernate validates mappings rather than modifying tables.

Country, indicator, and source catalogs use parameterized Spring Data JPA queries. Historical and latest-value projections use parameterized JDBC queries inside read-only service transactions. Responses preserve one series definition, source, unit, and annual frequency. Ambiguous histories require an explicit series ID. Query requests never call external providers.

The public series response includes a comparison policy with an `allowed` flag and an explanatory `note`. Multi-country history checks this policy before returning data. National poverty lines remain available for individual histories but are blocked for direct country comparison because their thresholds differ by country. Other definitions carry methodological context rather than implying complete harmonization.

Read the [API reference](../api.md), [data model](../data-model.md), [coverage reference](../data-coverage.md), and [World Bank adapter documentation](../integrations/world-bank.md).

## Data identity and provenance

The import catalogs define 27 indicators: 24 WDI measures and three OECD measures. Their existence does not guarantee observations for every country or year. Country observations retain decimal values, explicit missing status, provider status flags, validated statistical update dates when available, and independent collection timestamps. OECD transport-header dates are not assumed to describe the statistical revision; an unknown revision date remains null.

OECD average and minimum wages select annual pay periods in constant US dollars at purchasing-power parity. Their base year is read from the provider response and included in both the unit and dataset identity. A new base creates a separate series so an import cannot overwrite an older series with incompatible values. Household debt selects the ratio to net household disposable income, including non-profit institutions serving households; it is not substituted with a GDP ratio.

World highlights use the provider's published `WLD` observations for GDP, population, life expectancy, and Internet use. They have a separate observation table and share the corresponding indicator/series definitions. `WLD` is excluded from the country catalog. The overview endpoint does not compute a total or average from the home selection.

Taiwan's catalog identity is retained from the official IMF country catalog. This identity does not create WDI observations or infer region, income group, capital, or coordinates. The home selection and import scope are separate: the default WDI observation import targets the 24 selected locations available from that provider. Previously imported observations outside the configured scope remain stored and queryable.

## Runtime and imports

Docker Compose runs PostgreSQL, the backend, and an unprivileged Nginx frontend. Database health gates backend startup. Nginx serves the static React build with SPA routing and forwards `/api` to the backend. Vite provides an equivalent proxy during local development.

Published Compose ports bind to localhost. Actuator uses a separate loopback interface and port with only health exposed; the port is not published or proxied. Readiness checks the database. CORS allows exact configured origins and read methods without browser credentials.

The World Bank and OECD operational commands run internally and exit after completion. Their one-shot flags are mutually exclusive. Both use the configured `atlas.import` country and year scope; the OECD command runs after country catalog import and requires no additional credentials. Provider-specific PostgreSQL advisory locks prevent overlapping imports of the same source across application instances. Completed batches are idempotent, and observations outside the requested scope remain unchanged.

Scheduling is opt-in and refreshes World Bank data only. Either one-shot command suppresses scheduled World Bank jobs. OECD refreshes use its manual command. Ordinary application startup performs no upstream requests unless the World Bank schedule or an import command is explicitly enabled.

## Frontend

React Router separates the entry directory from the full data workspace:

| Route | Responsibility |
| --- | --- |
| `/` | Exactly 25 selected country/territory links, local selection search, persisted world highlights, and comparison access. |
| `/explorar` | Complete country catalog with paginated name/ISO search, region and income filters, and the shared dashboard. |
| `/paises/:iso3Code` | Individual country metadata, category navigation, latest values, and annual histories. |
| `/comparar` | Two to five countries, one indicator, and one explicit source-specific series. |
| `/sobre` | Product, sources, and interpretation guidance. |

The home country names follow the product's Brazilian Portuguese selection. Its search matches names without case/diacritic sensitivity and accepts ISO codes. The full catalog remains accessible independently of this selection. The interface uses text, restrained typography, native controls, and a monochrome light/dark palette without decorative emoji. Only the display theme is stored in localStorage; country selection, filters, indicator, series, and period are represented in URL parameters.

A shared country dashboard presents four headline indicators, category-filtered series, and one selected history. Comparison requires a single series ID, preserving its source, unit, dataset, frequency, and price base. Its summary uses the latest year in the selected interval with numeric observations for every selected country. If no common year exists, the summary remains unavailable while the individual histories retain their original years. No older value is carried forward to fill a gap.

Recharts renders historical and comparison charts in lazy-loaded bundles. Accessible tables expose the annual observations and distinguish explicit missing values from absent records. Country summaries preserve each indicator's latest numeric year. Source definitions, organizations, units, price bases, and provider/collection dates remain visible or available through disclosures. Comparison policy notes accompany the selected definition, with blocked comparisons linking back to individual profiles.

`services/api.ts` owns typed HTTP contracts, localized safe errors, timeouts, and complete collection retrieval. Query hooks integrate these requests with TanStack Query using country sets, series, and interval in cache keys. Obsolete requests are canceled; permanent client errors are not retried. Failed or inconsistent pages reject a history instead of presenting partial data. Search results remain paginated, while a separately cached full catalog supplies filter and comparison options. World highlights use their own overview query.

Presentation helpers format units in Brazilian Portuguese and preserve the distinction between zero and unavailable values. Null placeholders interrupt chart lines without creating source observations. Annual changes require numeric observations from consecutive years in the same series; appropriate unit-specific differences are used. The default interval covers 20 calendar years through the current UTC year, with 10-year, 20-year, since-1960, and validated custom intervals. Loading, empty, and failure states do not manufacture figures.

## Configuration and operations

Database credentials are environment variables and never reach the browser. Import scope and scheduling use environment variables or command arguments. There are no public administrative, write, or import endpoints. Logs use structured JSON; persisted import errors omit provider payloads and internal stack traces.

Backend Docker builds cache Maven project dependencies with BuildKit and compile the application with unit tests. Frontend Docker builds run `npm test` before `npm run build`. Browser checks run separately against the application and imported dataset. Swagger UI is served through the frontend proxy with an inline-style exception scoped to its own route; the application keeps its stricter content policy.

The Compose configuration is intended for local development. Deployment-specific TLS, network policy, and database roles belong to runtime configuration.
