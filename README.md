# Atlas Global

An open-source, free, publicly accessible platform for exploring international data. **Phase 1 — Foundation** provides the infrastructure, backend, and initial interface. External integrations, indicators, domain entities, and business endpoints have not been implemented yet. There are no accounts or authentication.

## Requirements

- Docker Engine with Docker Compose v2 to run the containers.
- For development outside containers: JDK 21, Maven 3.9+, Node.js 22.12+ from the 22.x release line, and npm.
- Default local ports: 3000 (Docker interface), 5173 (Vite), 8080 (backend), 8081 (local health checks), and 5432 (PostgreSQL).

## Run everything with Docker Compose

```bash
cp .env.example .env
```

Edit `.env` and set `DB_PASSWORD` to a local password. Compose rejects an empty password. Do not commit this file.

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs backend
```

Open **http://localhost:3000**. The interface includes a home page, an about page, and a theme toggle. The application does not display simulated figures or queries.

Management port 8081 is not published by Compose. To check the backend and database from inside the container:

```bash
docker compose exec backend wget -qO- http://127.0.0.1:8081/actuator/health/readiness
```

The expected response is `{"status":"UP"}`. The readiness group includes database connectivity. Backend startup fails if Flyway cannot apply or validate migrations.

Check the migration directly in the database:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, description, success FROM flyway_schema_history;"'
```

Version `1` should exist with `success = true`. The initial migration does not create domain tables. Flyway creates its own schema history table.

```bash
docker compose down
```

This command preserves the volume. Do not use `down -v` if you want to keep the database. Changing the password in `.env` does not change the password of an already initialized database; update it directly in PostgreSQL instead.

## Local development

Prepare `.env` as described above and start only the database:

```bash
docker compose up -d postgres
```

In a Bash terminal at the project root, export the local configuration. The shell executes the file, so only source the local `.env` you prepared yourself.

```bash
set -a
source .env
set +a
export DB_URL="jdbc:postgresql://127.0.0.1:${DB_PORT:-5432}/${DB_NAME:-atlas_global}"
mvn -f backend/pom.xml spring-boot:run
```

Make sure `java -version` and `mvn -version` point to Java 21. Adjust `JAVA_HOME` if necessary.

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Vite forwards `/api` to the local backend; this phase does not include business endpoints. The frontend does not require passwords or sensitive environment variables.

Local operational check:

```bash
curl --fail http://127.0.0.1:8081/actuator/health/readiness
```

`http://localhost:8080/actuator/health` should return **404** because management runs on a separate port. Only `health` is exposed on the management port; `env`, `beans`, and other endpoints are not published.

## Build and tests

Run backend unit tests and build without a database:

```bash
mvn -f backend/pom.xml verify
```

Run integration tests against **a dedicated, empty test database**, with PostgreSQL running:

```bash
export DB_URL=jdbc:postgresql://127.0.0.1:5432/atlas_test
export DB_USERNAME=atlas
export DB_PASSWORD='your-local-password'
mvn -f backend/pom.xml -Pintegration verify
```

Create `atlas_test` beforehand with the corresponding owner. This profile applies migration V1 and verifies connectivity, the absence of domain tables, and Actuator isolation. It does not delete tables or clean the database. Reusing a database containing other tables will intentionally cause the schema test to fail.

Frontend:

```bash
cd frontend
npm ci
npm run typecheck
npm run build
```

`package-lock.json` pins resolved versions; use `npm ci` to reproduce the installation. The shadcn/ui components live in the source tree (`components/ui`), with aliases and theme settings in `components.json`.

## Configuration

| Variable | Purpose |
| --- | --- |
| `DB_PASSWORD` | Required by the backend and Compose; never committed. |
| `DB_USERNAME` | Database username; defaults to `atlas`. |
| `DB_NAME`, `DB_PORT` | Database name and database port published by Compose. |
| `DB_URL` | JDBC URL for the local backend; Compose uses the `postgres` service. |
| `BACKEND_PORT`, `FRONTEND_PORT` | Ports published by Compose. |
| `SERVER_PORT` | HTTP port for the Java process outside Compose; defaults to 8080. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of exact origins; no wildcards. |
| `MANAGEMENT_PORT`, `MANAGEMENT_ADDRESS` | Operational management; defaults to 8081 and 127.0.0.1. Keep access restricted. |

Compose binds published ports to `127.0.0.1` only. This configuration is for local development, not production deployment. The PostgreSQL user created by the image is an operational account; separate database roles for production will be defined during the deployment phase.

## Project structure

```text
backend/         Spring Boot, configuration, migration, and tests
frontend/        React, Vite, Tailwind, and shadcn/ui components
infrastructure/  Local infrastructure guidance
docs/            Architecture decisions and validation
```

See the [initial architecture](docs/architecture/foundation.md) and [phase validation results](docs/validation.md). No push or deployment is included in this delivery.
