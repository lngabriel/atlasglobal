# Atlas Global

[GitHub](https://github.com/lngabriel/atlasglobal)

Atlas Global is an open-source platform for exploring public international data. Its backend imports World Bank country metadata and annual indicators into PostgreSQL, preserving source definitions, units, update dates, and missing values. Access is public and free, without accounts or authentication.

The web interface opens directly into a country explorer, with Brazil selected by default. Country profiles combine current indicator summaries, historical charts, and source documentation in a responsive editorial layout with light and dark themes. The interface is in Brazilian Portuguese; the public REST API also supports independent clients.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Web, Spring Data JPA, Hibernate, Validation, Actuator, and Springdoc OpenAPI.
- **Data:** PostgreSQL 17 and Flyway, with a World Bank API adapter and transactional batch imports.
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

A new database has no imported observations. Run the [World Bank import command](#import-world-bank-data) below to populate the country explorer and charts. The interface reports unavailable data without substituting example values.

To stop the services while preserving database contents:

```bash
docker compose down
```

Changing `.env` does not update credentials in an existing PostgreSQL volume. Update existing database credentials directly in PostgreSQL. Never commit `.env`.

## Explore the data

- Search countries and economies by name or ISO code, filter by region and income group, and browse paginated results. Quick links open Brazil, the United States, and China. On narrow screens, open **Pesquisar países** to access the search controls.
- Select a country to view GDP, population, GDP per capita, and life expectancy summaries, followed by its source-specific indicator catalog. Each value includes its own reference year; recent years can differ between series.
- Filter the indicator catalog by category and choose a series to inspect its annual history. The default interval covers 20 calendar years through the current UTC year. Use the 10-year, 20-year, or since-1960 presets, or enter a custom interval.
- Open **Ver dados em tabela** for the chart's accessible data table. It distinguishes a missing source value from a year with no imported record. Charts leave gaps in both cases and do not interpolate values.
- Expand **Definição e procedência** for the original definition, unit, source organization, dataset, and documentation. Source update and collection dates appear below the chart.

Country profiles have direct addresses such as `/paises/BRA`. Country selection, search filters, category, indicator, series, and interval are represented in the URL, allowing views to be bookmarked or shared. For example:

```text
http://localhost:3000/?pais=BRA&indicador=SP.POP.TOTL&de=2000&ate=2024
```

The catalog can contain countries without imported observations. Their profiles remain available and explicitly report the coverage gap.

## Public API

The read-only API is available at **http://localhost:3000/api/v1** through the frontend proxy, or **http://localhost:8080/api/v1** directly. Queries read persisted PostgreSQL data and do not contact external providers.

- [Interactive API documentation](http://localhost:3000/api/docs)
- [OpenAPI JSON](http://localhost:3000/api/openapi)
- [API reference](docs/api.md)

After importing data, query the country and indicator catalogs or retrieve an annual series:

```bash
curl --fail 'http://localhost:3000/api/v1/countries?q=Brasil&page=0&size=20'
curl --fail 'http://localhost:3000/api/v1/indicators?category=ECONOMY&source=WORLD_BANK'
curl --fail 'http://localhost:3000/api/v1/countries/BRA/indicators/NY.GDP.MKTP.CD?from=2020&to=2024'
```

Country searches support names, ISO codes, region, and income filters. Indicator searches support name/code, category, and source filters. Collections are paginated, with at most 100 records per page and explicit allowed sort fields. Historical responses include units, source metadata, and missing-value status; ambiguous indicators require an explicit `seriesId`.

All queries are public and require no account, token, or authentication header. The API has no public write, import, or administrative operations.

## Import World Bank data

Imports are operational commands, not public HTTP endpoints. With the database running and the backend image built:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.import.run-once=true
```

The command imports the country catalog and eight annual indicators for Brazil, the United States, China, Russia, Germany, Japan, the United Kingdom, France, and India. The default historical interval begins in 1960 and ends in the current UTC year. Source availability determines which periods are returned.

Override the scope using application arguments:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.import.run-once=true \
  --atlas.import.countries=BRA,USA,CHN \
  --atlas.import.from-year=2020 \
  --atlas.import.to-year=2024
```

The process exits after importing and returns a nonzero exit code on failure. Scheduled jobs are disabled for this one-shot process, even when scheduling is enabled in the environment. Repeating a command updates existing observations without creating duplicates. Previously stored periods outside the requested interval remain unchanged.

Scheduled refreshes are disabled by default. Set `WORLD_BANK_SCHEDULE_ENABLED=true` to enable a weekly refresh at 03:00 UTC on Sunday. Scheduled imports refresh the last six calendar years through the configured end year; run a full historical import periodically to capture older revisions. `WORLD_BANK_IMPORT_CRON` accepts a Spring six-field cron expression.

Inspect import history and coverage:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT started_at, completed_at, status, records_imported FROM data_import ORDER BY started_at DESC LIMIT 10;"'
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT status, count(*) FROM indicator_observation GROUP BY status;"'
```

`records_imported` counts observations processed during an execution, including missing values and updated records. A failed execution may retain completed page batches; rerunning the command safely resumes through idempotent upserts.

## Data source

The importer uses [World Bank Indicators API v2](https://datahelpdesk.worldbank.org/knowledgebase/articles/889392), specifically **World Development Indicators (source 2)**.

| Indicator | World Bank code | Stored unit |
| --- | --- | --- |
| GDP | `NY.GDP.MKTP.CD` | Current US dollars |
| GDP per capita | `NY.GDP.PCAP.CD` | Current US dollars per person |
| GDP growth | `NY.GDP.MKTP.KD.ZG` | Annual percent |
| Population | `SP.POP.TOTL` | Persons |
| Consumer price inflation | `FP.CPI.TOTL.ZG` | Annual percent |
| Unemployment, modeled ILO estimate | `SL.UEM.TOTL.ZS` | Percent of labor force |
| Life expectancy at birth | `SP.DYN.LE00.IN` | Years |
| Individuals using the Internet | `IT.NET.USER.ZS` | Percent of population |

Country aggregates are excluded. Country names are localized to Brazilian Portuguese where available, while original names and source metadata are retained. Missing observations are stored explicitly and never converted to zero. Data coverage and revision dates vary by indicator and country.

Consult the [World Bank data terms](https://www.worldbank.org/en/about/legal/terms-of-use-for-datasets) and the original source organizations recorded with each series when reusing data.

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

After building the backend, a local import can use the same exported database variables:

```bash
mvn -f backend/pom.xml verify
java -jar backend/target/atlas-global-0.1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --atlas.import.run-once=true
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
| `WORLD_BANK_COUNTRIES` | Comma-separated country codes; defaults to the nine countries listed above. |
| `WORLD_BANK_FROM_YEAR` | First import year, default 1960. |
| `WORLD_BANK_TO_YEAR` | Last import year; 0 means the current UTC year. |
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

For browser checks, start the Compose services and complete the default nine-country import, then run:

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
  src/pages/     Country explorer, country profile, and project information
  src/components/  Search, dashboard, historical chart, and reusable UI
  src/hooks/     Query hooks and display theme
  src/services/  Typed API client and query cache configuration
  src/lib/       Formatting, country labels, and historical transformations
  tests/         Unit tests and browser checks
infrastructure/  Local runtime configuration guidance
docs/            Technical architecture, data model, and provider documentation
```

Technical references: [API reference](docs/api.md), [data model](docs/data-model.md), and [World Bank integration](docs/integrations/world-bank.md).
