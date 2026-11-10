# Validation results

Verified on **2026-11-09** with Java 21.0.13, Maven 3.9.12, PostgreSQL 17.11, Node.js 22.23.4, Google Chrome, and Docker Engine 29.9.3. These results describe a tested local snapshot; official providers can revise values and coverage.

## Backend

```bash
mvn -f backend/pom.xml -Pintegration verify
```

**BUILD SUCCESS: 93 tests passed** — 65 unit/contract tests and 28 PostgreSQL integration tests, with no failures, errors, or skipped tests. Integration tests used a dedicated database; transactional store tests rolled back their writes.

Coverage includes:

- Explicit CORS, safe errors, read-only API methods, OpenAPI, and isolated Actuator health endpoints.
- World Bank pagination, metadata, aggregate exclusion, exact decimals, null versus zero, HTTP retries, invalid provider responses, and bounded import configuration.
- OECD annual dimension selection, RFC 4180 CSV, exact unit multipliers, missing values, zero, duplicate rejection, distinct price bases, retries, and transport dates that must not become statistical revision dates.
- IBGE metadata, variables, national geography, classifications, annual periods, coefficient scale, price-base identity, exact decimals, SIDRA symbols, bounded gzip, duplicate keys, and HTTP failures.
- BCB strict monthly dates, exact December selection, complete-year boundaries, precision, missing values, duplicates, retries, and catalog prerequisites.
- Flyway migrations, JPA mappings, transactional upserts, idempotency, retained history, incompatible series rejection, per-provider import coordination, and one-shot scheduling isolation.
- Country/source filters, stable pagination, source-specific histories, separate world observations, national-summary coverage, and server-side rejection of direct national-poverty and Brazil-only comparisons. Every pair of one-shot commands is rejected before execution. Provider spies confirm that public queries do not contact external sources.

## Frontend and browser

```bash
cd frontend
npm test
npm run build
npm run test:browser
```

**46 frontend unit tests passed**, with no failures or skipped tests. TypeScript and the Vite production build passed. Unit coverage includes complete paginated retrieval, cancellation, safe errors, localized units, absent-year gaps, zero versus missing values, annual changes, country search, comparison selection, and latest common-year calculations. National tests preserve three-decimal Gini values and changes, dynamic real-income price bases, and the distinct BCB denominator. Inconsistent units, countries, or failed intermediate pages reject the complete history rather than exposing partial data.

**45 Chrome checks passed** against the production application served by Nginx at `http://127.0.0.1:3000`, with all four documented provider imports loaded:

- The home directory contains exactly the 25 selected countries and territories, with direct profile links and no emoji flags. Selection search works.
- All four global highlights match persisted `WLD` values, individual reference years, and source metadata.
- The complete explorer retains search, region/income filters, category filtering, pagination, shared URLs, and individual profiles.
- Annual histories, period presets, custom single-year intervals, keyboard chart tooltips, accessible tables, and persistent themes work.
- Loading and controlled network failures do not display stale charts; retry restores the history.
- Two-to-five-country comparisons use one series and a common reference year. Displayed values and annual tables match the real API.
- Gini shows its methodological caveat. National-poverty comparison is blocked without a history request. Unavailable Taiwan observations remain unavailable.
- The OECD wage profile matches its actual annual PPP value, source, and reported constant-price base; its unknown statistical update date remains null.
- All three national histories match their actual imported values, scales, years, units, and sources. IBGE income retains its published price base. National comparison remains blocked without a history request and offers Brazil's individual profile.
- Home, comparison, and explorer layouts have no page-level horizontal overflow at 1440, 768, 390, and 320 pixels. Mobile country selection preserves visible keyboard focus.
- Unknown countries/routes and countries without observations show their corresponding recoverable states. No JavaScript exceptions, content policy violations, or unexpected API errors occurred.

Desktop and mobile screenshots were visually reviewed in light and dark themes. Backend and frontend Docker images built successfully; their builds execute unit tests before packaging.

## Live imports and database integrity

The application database contains completed World Bank and OECD imports. Both new national commands also completed with exit code 0 and persisted `COMPLETED` execution records. Read-only database checks found:

| Stored records | Count |
| --- | ---: |
| Country/economy identities | 218 |
| Indicator definitions | 30 |
| Source-specific series | 30 |
| Country observations | 40,541 |
| Numeric country observations | 22,866 |
| Explicitly missing country observations | 17,675 |
| Published world observations | 264 |

All country/series/year keys and world series/year keys are unique. Previously imported Germany and India histories remain present, with 528 records each. Taiwan has a catalog identity and zero invented observations.

The OECD execution imported **1,420 records**: 487 average-wage records across 14 selected countries, 601 minimum-wage records across 13, and 332 household-debt records across 13. The wage response reported a 2025 constant-price base, preserved in the stored unit and series identity. All OECD statistical update dates remain null because the response does not establish a validated revision date; independent collection timestamps are present.

The IBGE command imported **28 observations**: 14 annual Gini coefficients and 14 annual real monthly household-income values, covering 2012–2025. The BCB command imported **21 December observations**, covering completed years 2005–2025. Their latest values matched the official responses: Gini 0.511, monthly household income per person R$ 2,264 at 2025 prices, and December household debt 49.70% of restricted RNDBF. See the [Brazilian source reference](integrations/brazil.md).

National observations exist only for Brazil, with no invented statistical revision dates. The 39,072 World Bank and 1,420 OECD country observations remained unchanged in count; the 264 world records remained separate. All 40,541 country/series/year keys are unique.

The application Compose services remain available. The isolated integration-test database is stopped after verification. Operational readiness includes PostgreSQL connectivity; management endpoints remain private.
