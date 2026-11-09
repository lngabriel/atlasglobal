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

React Router manages navigation. TanStack Query is configured for data fetching without placeholder queries. The UI uses Tailwind CSS and local shadcn/ui components with a monochrome light/dark palette. Only the display theme is stored in localStorage.

The application currently presents an indicator workspace and source information. Imported data is available through the public REST API and Swagger UI; the React workspace does not yet display analytical queries. No synthetic figures are presented.

## Configuration and operations

Database credentials are environment variables and never reach the browser. Import scope and scheduling are configured through environment variables or command arguments. No public administrative or import endpoints exist. Logs use structured JSON; persisted errors omit internal stack traces and provider payloads.

Docker builds use BuildKit cache mounts for Maven project dependencies; builds still compile and run unit tests. Swagger UI is available through the frontend proxy, with an inline-style exception scoped to its own route while the application keeps its stricter policy.

The Compose configuration is intended for local development. Deployment-specific TLS, network policy, and database roles are operational concerns outside this configuration.
