# Atlas Global

[GitHub](https://github.com/lngabriel/atlasglobal)

Atlas Global is an open-source platform for exploring public international data. Its backend imports World Bank country metadata and annual indicators into PostgreSQL, preserving source definitions, units, update dates, and missing values. Access is public and free, without accounts or authentication.

The web interface provides a minimal indicator workspace with light and dark themes. Imported observations are currently available in the database; the web interface does not yet expose data queries.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Web, Spring Data JPA, Hibernate, Validation, and Actuator.
- **Data:** PostgreSQL 17 and Flyway, with a World Bank API adapter and transactional batch imports.
- **Frontend:** React, TypeScript, Vite, Tailwind CSS, shadcn/ui, React Router, and TanStack Query.
- **Runtime:** Docker Compose, with separate backend, frontend, and database services.

## Quick start

Install Docker Engine and Docker Compose v2 or later, then clone the repository:

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

To stop the services while preserving database contents:

```bash
docker compose down
```

Changing `.env` does not update credentials in an existing PostgreSQL volume. Update existing database credentials directly in PostgreSQL. Never commit `.env`.

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

Integration tests verify migrations, entity mappings, transactional upserts, precision, import history, concurrency protection, and Actuator isolation. Import store tests roll back their changes. Provider contract tests use recorded response fixtures and controlled error responses; production imports always use the real API.

```bash
cd frontend
npm ci
npm run typecheck
npm run build
```

Use `npm ci` to reproduce the dependency versions in `package-lock.json`.

## Project structure

```text
backend/         Spring Boot application, provider adapters, migrations, and tests
frontend/        React application and reusable UI components
infrastructure/  Local runtime configuration guidance
docs/            Technical architecture, data model, and provider documentation
```

Technical references: [data model](docs/data-model.md) and [World Bank integration](docs/integrations/world-bank.md).
