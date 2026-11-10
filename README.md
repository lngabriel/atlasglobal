# Atlas Global

[GitHub](https://github.com/lngabriel/atlasglobal)

Atlas Global is an open-source platform for exploring public international data. Its backend imports official World Bank and OECD annual statistics into PostgreSQL, preserving source definitions, units, price bases, update dates, and missing values. Access is public and free, without accounts or authentication.

The home page presents a focused selection of 25 countries and territories, published world aggregates, and access to country comparison. Country profiles combine indicator summaries, historical charts, and source documentation in a responsive editorial layout with light and dark themes. The interface is in Brazilian Portuguese; the public REST API also supports independent clients.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Web, Spring Data JPA, Hibernate, Validation, Actuator, and Springdoc OpenAPI.
- **Data:** PostgreSQL 17 and Flyway, with World Bank and OECD adapters and transactional batch imports.
- **Frontend:** React, TypeScript, Vite, Tailwind CSS, shadcn/ui, React Router, TanStack Query, and Recharts 3.
- **Runtime:** Docker Compose, with separate backend, frontend, and database services.

## Quick start

Install Docker Engine with BuildKit enabled and Docker Compose v2 or later, then clone the repository:

```bash
git clone https://github.com/lngabriel/atlasglobal.git
cd atlasglobal
cp .env.example .env
```

Set `DB_PASSWORD` in `.env`. If a local PostgreSQL instance already uses port 5432, set `DB_PORT=5433`.

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

Open **http://localhost:3000**. The database and its configured owner are created automatically when the PostgreSQL volume is initialized. Flyway applies and validates migrations when the backend starts.

A new database has no imported observations. Run the [World Bank import command](#import-world-bank-data) below, followed by the [OECD import](#import-oecd-data), to populate the catalogs, world highlights, and charts. The interface reports unavailable data without substituting example values.

To stop the services while preserving database contents:

```bash
docker compose down
```

Changing `.env` does not update credentials in an existing PostgreSQL volume. Update existing database credentials directly in PostgreSQL. Never commit `.env`.

## Explore the data

The home selection contains exactly these countries and territories, displayed using their Brazilian Portuguese names: Argentina, Austrália, Brasil, Canadá, Chile, China, França, Hong Kong, Israel, Itália, Japão, Paraguai, Polônia, Portugal, Rússia, Singapura, Coreia do Sul, Espanha, Suíça, Taiwan, Ucrânia, Reino Unido, Estados Unidos, Venezuela, and Vietnã. Search this selection by name or ISO code and open a country profile directly.

- **Home (`/`):** a compact country directory and four global highlights: GDP, population, life expectancy, and Internet use. Global figures are the World Bank's published `WLD` aggregates, stored separately from country observations. They are not totals or averages of the selected countries.
- **Complete explorer (`/explorar`):** the full country/economy catalog, with name/ISO search, region and income filters, and pagination. Countries outside the home selection remain accessible.
- **Country profiles (`/paises/:iso3Code`):** headline indicators, category navigation, all available series, and a selected annual history. Each value retains its own reference year, which may differ from another indicator's year.
- **Comparison (`/comparar`):** select two to five countries, one indicator, and one explicit source-specific series. The summary uses the latest year in the selected interval with numeric values for every selected country. If there is no common year, it reports that absence rather than carrying earlier values forward.

Historical views default to 20 calendar years through the current UTC year. Use the 10-year, 20-year, or since-1960 presets, or enter a custom interval. Open **Ver dados em tabela** for accessible annual values. Charts and tables distinguish an explicit missing value from a year without an imported record; neither is replaced by zero or interpolated.

Expand **Definição e procedência** to inspect the original definition, unit, organization, dataset, and documentation. Comparison views display methodological limitations. National poverty lines are excluded from direct country comparisons because thresholds differ between countries. Sharing a provider and code does not remove survey, coverage, or national-definition differences; read the [coverage and comparability reference](docs/data-coverage.md).

Selection and filters are represented in the URL, allowing views to be bookmarked or shared:

```text
http://localhost:3000/paises/BRA?indicador=SP.POP.TOTL&de=2000&ate=2024
http://localhost:3000/explorar?pais=BRA&categoria=ECONOMY
http://localhost:3000/comparar?paises=BRA,USA&indicador=SP.POP.TOTL&de=2000&ate=2024
```

A catalog entry does not guarantee observations. Taiwan's identity is retained from the official IMF country catalog, without inventing WDI observations or unavailable demographic metadata. Countries outside the home selection remain accessible through the complete explorer; changing the import scope does not delete their existing histories.

## Public API

The read-only API is available at **http://localhost:3000/api/v1** through the frontend proxy, or **http://localhost:8080/api/v1** directly. Queries read persisted PostgreSQL data and do not contact external providers.

- [Interactive API documentation](http://localhost:3000/api/docs)
- [OpenAPI JSON](http://localhost:3000/api/openapi)
- [API reference](docs/api.md)

After importing data, query the country and indicator catalogs or retrieve an annual series:

```bash
curl --fail 'http://localhost:3000/api/v1/overview'
curl --fail 'http://localhost:3000/api/v1/countries?q=Brasil&page=0&size=20'
curl --fail 'http://localhost:3000/api/v1/indicators?category=ECONOMY&source=WORLD_BANK'
curl --fail 'http://localhost:3000/api/v1/countries/BRA/indicators/NY.GDP.MKTP.CD?from=2020&to=2024'
```

Country searches support names, ISO codes, region, and income filters. Indicator searches support name/code, category, and source filters. Collections are paginated, with at most 100 records per page and explicit allowed sort fields. Historical responses include units, source metadata, missing-value status, and a comparison policy; ambiguous indicators require an explicit `seriesId`. Multi-country requests for non-comparable national poverty lines return HTTP 409.

All queries are public and require no account, token, or authentication header. The API has no public write, import, or administrative operations.

## Import World Bank data

Imports are operational commands, not public HTTP endpoints. With the database running and the backend image built:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.import.run-once=true
```

The command imports the eligible World Bank country catalog and 24 WDI indicator definitions. Its default observation scope is the home selection excluding Taiwan, which is not published in WDI: 24 countries and territories. It also imports the published world aggregate for the four home highlights into a separate table. The historical interval begins in 1960 and ends in the current UTC year by default. Source availability determines which periods are returned; previous observations outside the configured scope are retained.

Override the scope using application arguments:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.import.run-once=true \
  --atlas.import.countries=BRA,USA,CHN \
  --atlas.import.from-year=2020 \
  --atlas.import.to-year=2024
```

The process exits after importing and returns a nonzero exit code on failure. Scheduled jobs are disabled in either provider's one-shot process, even when scheduling is enabled in the environment. The World Bank and OECD one-shot flags are mutually exclusive. Repeating a command updates existing observations without creating duplicates. Previously stored periods outside the requested interval remain unchanged.

Scheduled refreshes are disabled by default. Set `WORLD_BANK_SCHEDULE_ENABLED=true` to enable a weekly refresh at 03:00 UTC on Sunday. This schedule refreshes World Bank data only, covering the last six calendar years through the configured end year; run a full historical import periodically to capture older revisions. OECD imports are manual. `WORLD_BANK_IMPORT_CRON` accepts a Spring six-field cron expression.

Inspect import history and coverage:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT started_at, completed_at, status, records_imported FROM data_import ORDER BY started_at DESC LIMIT 10;"'
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT status, count(*) FROM indicator_observation GROUP BY status;"'
```

`records_imported` counts observations processed during an execution, including missing values and updated records. A failed execution may retain completed page batches; rerunning the command safely resumes through idempotent upserts.

## Import OECD data

Run this command after importing the country catalog with the World Bank command:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.oecd-import.run-once=true
```

It imports three annual OECD definitions: average wages, real statutory minimum wages, and household debt. It uses the same `atlas.import.countries`, `atlas.import.from-year`, and `atlas.import.to-year` configuration as the World Bank importer. No additional environment variable or API credential is required. Do not enable both provider one-shot flags in one process.

The wage series use annual pay periods and constant US dollars at purchasing-power parity. The provider's price base is included in the stored unit and dataset identity; rebased observations form a separate series. Average wages refer to gross annual remuneration per full-time-equivalent dependent employee, while minimum wages refer to statutory annual amounts. Household debt is measured against net disposable income and includes non-profit institutions serving households. Country coverage differs between these measures.

The command records its execution and exits after completion. Repeating it updates matching observations without duplicating country/series/year keys. It does not run automatically on ordinary application startup, and the optional World Bank scheduler does not refresh OECD data.

## Data sources and coverage

The import catalogs define **27 indicators: 24 from World Development Indicators (WDI source 2) and three from the OECD**. An indicator definition does not imply that every country or year has a value.

| Subject | Integrated measures |
| --- | --- |
| Economy | GDP, GDP per capita, GDP growth, inflation, unemployment, and GDP per capita at PPP. |
| Population, health, and technology | Population, life expectancy, and Internet use. |
| Inequality and poverty | Gini, three international poverty thresholds at 2021 PPP, national poverty, five quintile shares, and the highest-decile income/consumption share. |
| Safety | Intentional homicides per 100,000 people. |
| Migration | Net migration and international migrant stock, in persons and as a population percentage. |
| Wages and household finances | OECD average annual wages, real annual minimum wages, and household debt relative to net disposable income. |

[World Bank Indicators API v2](https://datahelpdesk.worldbank.org/knowledgebase/articles/889392) supplies the WDI data, including original source-organization metadata. [OECD SDMX](https://www.oecd.org/en/data/insights/data-explainers/2024/09/api.html) supplies the earnings and household-debt series. Query endpoints read persisted data rather than calling these providers.

The [coverage reference](docs/data-coverage.md) explains methodological limits and concepts without a validated integrated series. The application does not present GDP at PPP as a wage or household purchasing-power index, migrant stock as an immigration rate or foreign-national count, or intentional homicide as general crime or all violent deaths. It does not estimate poverty population counts from separate population denominators.

Original names, methods, units, provider status flags, and dates are retained. Source update dates are recorded only when a validated statistical revision date is available; collection timestamps are recorded separately. OECD transport-header dates are not treated as statistical revision dates. Missing values remain explicitly unavailable. Consult the [World Bank data terms](https://www.worldbank.org/en/about/legal/terms-of-use-for-datasets), OECD data terms linked from its API documentation, and the source organizations associated with each series when reusing data.

## Local development

Requirements: JDK 21, Maven 3.9+, Node.js 22.12+ from the 22.x release line, and npm.

Start PostgreSQL:

```bash
docker compose up -d postgres
```

In Bash at the repository root, load your own local configuration and start the backend:

```bash
set -a
source .env
set +a
export DB_URL="jdbc:postgresql://127.0.0.1:${DB_PORT:-5432}/${DB_NAME:-atlas_global}"
mvn -f backend/pom.xml spring-boot:run
```

Verify that `java -version` and `mvn -version` use Java 21. In a second terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Vite proxies `/api` to the backend on port 8080. The frontend does not receive database credentials.

After building the backend, local imports can use the same exported database variables. Run the World Bank command first, then the OECD command in a separate process:

```bash
mvn -f backend/pom.xml verify
java -jar backend/target/atlas-global-0.1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --atlas.import.run-once=true
java -jar backend/target/atlas-global-0.1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --atlas.oecd-import.run-once=true
```

## Configuration

| Variable | Default / purpose |
| --- | --- |
| `DB_PASSWORD` | Required; never committed. |
| `DB_USERNAME` | Database owner, default `atlas`. |
| `DB_NAME` | Database name, default `atlas_global`. |
| `DB_PORT` | Local PostgreSQL port, default 5432. |
| `DB_URL` | JDBC URL for local Java execution; Compose uses the internal `postgres` service. |
| `BACKEND_PORT`, `FRONTEND_PORT` | Published ports, default 8080 and 3000. |
| `CORS_ALLOWED_ORIGINS` | Exact allowed browser origins, comma-separated; no wildcards. |
| `WORLD_BANK_COUNTRIES` | Comma-separated observation scope for both providers; defaults to the 24 home-selection codes supported by WDI, excluding Taiwan. |
| `WORLD_BANK_FROM_YEAR` | First import year for both providers, default 1960. |
| `WORLD_BANK_TO_YEAR` | Last import year for both providers; 0 means the current UTC year. |
| `WORLD_BANK_SCHEDULE_ENABLED` | Enables scheduled imports, default `false`. |
| `WORLD_BANK_IMPORT_CRON` | Refresh schedule, default `0 0 3 * * SUN`, evaluated in UTC. |
| `MANAGEMENT_PORT`, `MANAGEMENT_ADDRESS` | Operational health endpoint, default 8081 and 127.0.0.1. |

The Compose setup binds published ports to localhost. It is a local development configuration; keep management endpoints private in any deployment.

## Health checks

The readiness check includes database connectivity:

```bash
# Inside the backend container
docker compose exec backend wget -qO- http://127.0.0.1:8081/actuator/health/readiness

# When running Java locally
curl --fail http://127.0.0.1:8081/actuator/health/readiness
```

The expected response is `{"status":"UP"}`. Only health endpoints are exposed on the separate management port, without component details. Compose does not publish that port. `/actuator/health` on the main application port returns 404.

## Tests

Unit tests do not require PostgreSQL or external API access:

```bash
mvn -f backend/pom.xml verify
```

For integration tests, create a dedicated PostgreSQL test database and provide its credentials:

```bash
export DB_URL=jdbc:postgresql://127.0.0.1:5433/atlas_test
export DB_USERNAME=atlas
export DB_PASSWORD='your-local-test-password'
mvn -f backend/pom.xml -Pintegration verify
```

Integration tests verify migrations, entity mappings, transactional upserts, precision, import history, concurrency protection, public API contracts, filtering, pagination, validation, and Actuator isolation. Import store tests roll back their changes. Provider contract tests use recorded response fixtures and controlled error responses; production imports always use the real API.

```bash
cd frontend
npm ci
npm test
npm run typecheck
npm run build
```

Frontend unit tests use Node.js 22's built-in test runner. They verify API pagination and cancellation, safe errors, formatting, missing-value handling, and historical calculations without requiring a running backend. The frontend Docker build runs these tests before compiling the production bundle.

For browser checks, start the Compose services and complete the documented imports, then run:

```bash
cd frontend
npm run test:browser
```

The browser suite requires a local Google Chrome installation and checks the running application. `ATLAS_BASE_URL` overrides the default `http://127.0.0.1:3000`; `CHROME_BIN` selects the Chrome executable.

Use `npm ci` to reproduce the dependency versions in `package-lock.json`. Consult [validation results](docs/validation.md) for recorded checks and their environment.

## Project structure

```text
backend/         Spring Boot application, provider adapters, migrations, and tests
frontend/
  src/app/       Route configuration
  src/pages/     Home directory, complete explorer, country profile, comparison, and project information
  src/components/  World highlights, search, dashboards, historical/comparison charts, and reusable UI
  src/hooks/     Query hooks and display theme
  src/services/  Typed API client and query cache configuration
  src/lib/       Formatting, country labels, and historical transformations
  tests/         Unit tests and browser checks
infrastructure/  Local runtime configuration guidance
docs/            Technical architecture, data model, and provider documentation
```

Technical references: [API reference](docs/api.md), [architecture](docs/architecture/foundation.md), [data model](docs/data-model.md), [coverage and comparability](docs/data-coverage.md), and [World Bank integration](docs/integrations/world-bank.md).
