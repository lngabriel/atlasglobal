# Validation results

Verified on **2026-11-09** with Java 21.0.13, Maven 3.9.12, PostgreSQL 17.11, Node.js 22.23.4, Google Chrome, and Docker Engine 29.9.3. These results describe a tested local snapshot; official providers can revise values and coverage.

## Backend

```bash
mvn -f backend/pom.xml -Pintegration verify
```

**BUILD SUCCESS: 63 tests passed** — 36 unit/contract tests and 27 PostgreSQL integration tests, with no failures, errors, or skipped tests. Integration tests used a dedicated database; transactional store tests rolled back their writes.

Coverage includes:

- Explicit CORS, safe errors, read-only API methods, OpenAPI, and isolated Actuator health endpoints.
- World Bank pagination, metadata, aggregate exclusion, exact decimals, null versus zero, HTTP retries, invalid provider responses, and bounded import configuration.
- OECD annual dimension selection, RFC 4180 CSV, exact unit multipliers, missing values, zero, duplicate rejection, distinct price bases, retries, and transport dates that must not become statistical revision dates.
- Flyway migrations, JPA mappings, transactional upserts, idempotency, retained history, incompatible series rejection, per-provider import coordination, and one-shot scheduling isolation.
- Country/source filters, stable pagination, source-specific histories, separate world observations, and server-side rejection of direct national-poverty comparisons. Provider spies confirm that public queries do not contact external sources.

## Frontend and browser

```bash
cd frontend
npm test
npm run build
npm run test:browser
```

**40 frontend unit tests passed**, with no failures or skipped tests. TypeScript and the Vite production build passed. Unit coverage includes complete paginated retrieval, cancellation, safe errors, localized units, absent-year gaps, zero versus missing values, annual changes, country search, comparison selection, and latest common-year calculations. Inconsistent units, countries, or failed intermediate pages reject the complete history rather than exposing partial data.

**41 Chrome checks passed** against the production application served by Nginx at `http://127.0.0.1:3000`, with both documented provider imports loaded:

- The home directory contains exactly the 25 selected countries and territories, with direct profile links and no emoji flags. Selection search works.
- All four global highlights match persisted `WLD` values, individual reference years, and source metadata.
- The complete explorer retains search, region/income filters, category filtering, pagination, shared URLs, and individual profiles.
- Annual histories, period presets, custom single-year intervals, keyboard chart tooltips, accessible tables, and persistent themes work.
- Loading and controlled network failures do not display stale charts; retry restores the history.
- Two-to-five-country comparisons use one series and a common reference year. Displayed values and annual tables match the real API.
- Gini shows its methodological caveat. National-poverty comparison is blocked without a history request. Unavailable Taiwan observations remain unavailable.
- The OECD wage profile matches its actual annual PPP value, source, and reported constant-price base; its unknown statistical update date remains null.
- Home, comparison, and explorer layouts have no page-level horizontal overflow at 1440, 768, 390, and 320 pixels. Mobile country selection preserves visible keyboard focus.
- Unknown countries/routes and countries without observations show their corresponding recoverable states. No JavaScript exceptions, content policy violations, or unexpected API errors occurred.

Desktop and mobile screenshots were visually reviewed in light and dark themes. Backend and frontend Docker images built successfully; their builds execute unit tests before packaging.

## Live imports and database integrity

The expanded World Bank import and the OECD import both completed with exit code 0 and persisted `COMPLETED` execution records. Read-only database checks found:

| Stored records | Count |
| --- | ---: |
| Country/economy identities | 218 |
| Indicator definitions | 27 |
| Source-specific series | 27 |
| Country observations | 40,492 |
| Numeric country observations | 22,817 |
| Explicitly missing country observations | 17,675 |
| Published world observations | 264 |

All country/series/year keys and world series/year keys are unique. Previously imported Germany and India histories remain present, with 528 records each. Taiwan has a catalog identity and zero invented observations.

The OECD execution imported **1,420 records**: 487 average-wage records across 14 selected countries, 601 minimum-wage records across 13, and 332 household-debt records across 13. The wage response reported a 2025 constant-price base, preserved in the stored unit and series identity. All OECD statistical update dates remain null because the response does not establish a validated revision date; independent collection timestamps are present.

The first OECD live attempt exposed an ISO-formatted HTTP resource date that predated newer observations. After the adapter correction and regression test, the full retry completed successfully. The failed execution remains in import history for audit; no import history was deleted.

The application Compose services remain available. The isolated integration-test database is stopped after verification. Operational readiness includes PostgreSQL connectivity; management endpoints remain private.
