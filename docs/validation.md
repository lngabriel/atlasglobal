# Validation results

Verified on 2026-11-06 using Java 21.0.13, Maven 3.9.12, PostgreSQL 17.11, Node.js 22.23.4, and Docker Engine 29.9.3.

## Automated backend checks

```bash
mvn -f backend/pom.xml -Pintegration verify
```

Result: **BUILD SUCCESS**, 22 tests passed (17 unit/contract tests and five PostgreSQL integration tests), without failures or skipped tests.

Coverage includes explicit CORS policy, safe error responses, World Bank response decoding, country aggregate exclusion, metadata normalization, decimal precision, missing values versus zero, pagination completeness, transient and permanent HTTP failures, provider error envelopes, import configuration limits, Flyway migrations, JPA mappings, transactional upserts, historical retention, import history, concurrent import exclusion, and Actuator isolation.

Tests use a dedicated database. Store integration tests run in transactions that roll back their writes.

## Live provider checks

The real World Bank API was queried before adapter implementation. A full historical import for the nine configured countries completed with:

- 217 nonaggregate country/economy catalog entries.
- Eight indicator definitions and eight source-specific series.
- 4,752 annual observations: 4,007 numeric values and 745 explicit missing values.

Repeating the full import left 4,752 unique country/series/year observations. A persisted Brazil GDP value was checked against the original provider response. See the [provider documentation](integrations/world-bank.md) for the snapshot and tested request shapes.

## Frontend and containers

- TypeScript and Vite production build passed.
- Backend and frontend Docker images built successfully.
- All three services started through Compose using an isolated project and alternate localhost ports.
- Backend readiness returned `{"status":"UP"}`.
- Nginx served the SPA fallback at `/sobre`.
- The documented internal import command completed inside Docker, importing 216 observations for 2022–2024.
- An unavailable country produced exit code 1 and a FAILED import without deleting previously imported data.

The checks used isolated containers and databases; the user's existing database and local application services were not modified. These results describe the tested snapshot and do not guarantee permanent upstream availability or coverage.
