# Validation results

Environment verified using Java 21.0.13, Maven 3.9.12, PostgreSQL 17.11, Node.js 22.23.4, and Docker Engine 29.9.3.

## Automated backend checks

```bash
mvn -f backend/pom.xml -Pintegration verify
```

Last run on 2026-11-09: **BUILD SUCCESS**, 40 tests passed (18 unit/contract tests and 22 PostgreSQL integration tests), without failures or skipped tests.

Coverage includes explicit CORS policy, safe error responses, World Bank response decoding, country aggregate exclusion, metadata normalization, decimal precision, missing values versus zero, pagination completeness, transient and permanent HTTP failures, provider error envelopes, import configuration limits, Flyway migrations, JPA mappings, transactional upserts, historical retention, import history, concurrent import exclusion, incompatible unit rejection, one-shot scheduling isolation, and Actuator isolation. Public API tests cover country/source filters, stable pagination, literal search, source-specific histories, missing values, explicit series selection, OpenAPI, and rejection of write methods. A provider spy verifies that query requests do not call external sources.

Tests use a dedicated database. Store integration tests run in transactions that roll back their writes.

## Live provider checks

The real World Bank API was queried before adapter implementation. The following snapshot was verified on 2026-11-06. A full historical import for the nine configured countries completed with:

- 217 nonaggregate country/economy catalog entries.
- Eight indicator definitions and eight source-specific series.
- 4,752 annual observations: 4,007 numeric values and 745 explicit missing values.

Repeating the full import left 4,752 unique country/series/year observations. A persisted Brazil GDP value was checked against the original provider response. See the [provider documentation](integrations/world-bank.md) for the snapshot and tested request shapes.

## Frontend and containers

Isolated environment verified on 2026-11-06:

- TypeScript and Vite production build passed.
- Backend and frontend Docker images built successfully.
- All three services started through Compose using an isolated project and alternate localhost ports.
- Backend readiness returned `{"status":"UP"}`.
- Nginx served the SPA fallback at `/sobre`.
- The documented internal import command completed inside Docker, importing 216 observations for 2022–2024.
- An unavailable country produced exit code 1 and a FAILED import without deleting previously imported data.

The original container checks used isolated databases. These results describe tested snapshots; upstream data availability and coverage can change.


## Application database verification — 2026-11-09

The backend image was rebuilt and updated through Docker Compose. The documented one-shot import completed against the configured application database with exit code 0 and a COMPLETED import record.

- 217 country/economy catalog entries and eight indicator definitions.
- 4,752 observations with 4,752 distinct country/series/year keys.
- 4,007 numeric values and 745 explicitly missing values.
- 528 observations each for BRA, USA, CHN, RUS, DEU, JPN, GBR, FRA, and IND.
- Backend readiness returned `{"status":"UP"}` after the import.

The previously loaded observations were updated without duplication. The application services remain available; the isolated test database is stopped after verification.


## Public API verification — 2026-11-09

The backend and frontend proxy were rebuilt and updated through Compose. Live HTTP checks passed through port 3000 for country, indicator, and source catalogs, country summaries, and annual histories using the imported World Bank dataset.

- Brazil GDP history for 2020–2024 returned five observations with USD_CURRENT and World Bank metadata.
- The same interval for Brazil and the United States returned ten raw observations from one series.
- Invalid pagination returned 400, an unknown country returned 404, and POST to a query collection returned 405, with ProblemDetail responses.
- The OpenAPI 3.0 document exposed nine paths containing only GET operations and correctly typed error schemas.
- Swagger UI, its assets, and its configuration loaded through the proxy. A Chrome keyboard/mouse check used “Try it out” to fetch Brazil with HTTP 200 and no authentication.
- The Swagger inline-style exception is scoped to `/api/swagger-ui/`; the main application retains its stricter content policy.
- Readiness remained UP. The application database retained 4,752 unique observations and two completed import executions, confirming that these checks did not initiate an import.

Docker builds now cache Maven project dependencies with BuildKit and still compile the application and execute unit tests. PostgreSQL integration tests run separately against the isolated test database.

## Country explorer verification — 2026-11-09

```bash
cd frontend
npm test
npm run build
npm run test:browser
```

The frontend unit suite passed **22 tests**, with no failures or skipped tests. Coverage includes complete API pagination, cancellation, failed intermediate pages, safe errors, numeric zero versus missing values, localized formatting, absent-year chart gaps, and comparable annual changes. The TypeScript and Vite production build passed. The frontend Docker build also executes the unit suite before compiling the application.

The Chrome suite passed **24 checks** against the production application served by Nginx at `http://127.0.0.1:3000`, using the persisted World Bank dataset:

- Headline GDP and individual indicator reference years matched the API responses.
- Featured country selection, name search, region/income filters, category filtering, pagination, and shared URL state worked.
- Empty country parameters and out-of-range country pages recovered to a usable workspace.
- Direct country routes, annual histories, period presets, a custom single-year interval, and the accessible data table worked.
- Explicit missing observations and years without an imported record remained distinct; neither became zero.
- Keyboard navigation exposed chart tooltips. The theme preference survived reload.
- Paused requests displayed loading without a stale chart. A controlled connection failure displayed a recoverable error, and retry restored the chart.
- Layouts at 1440, 390, and 320 pixels had no page-level horizontal overflow. Mobile country selection preserved focus on a visible control.
- A known country without observations, an unknown country, and an unknown route displayed their corresponding empty/error states.
- No JavaScript exceptions, content policy violations, or unexpected API errors occurred.

Desktop and mobile screenshots were visually reviewed in light and dark themes. On narrow screens, the indicator table keeps the reference year and source within each indicator row to preserve readable names and values.

The backend readiness endpoint remained `{"status":"UP"}`. Read-only database checks confirmed 4,752 observations with 4,752 distinct country/series/year keys and two completed imports. These frontend checks did not initiate an import or modify application data. The existing backend test results above remain a separate verification record.
