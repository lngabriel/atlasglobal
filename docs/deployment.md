# Deployment: Cloudflare Pages, Render, and Neon

The free deployment uses Cloudflare Pages for static React assets, a Render Free Docker web service for Spring Boot, and Neon Free PostgreSQL. The initial address is `https://YOUR_PROJECT.pages.dev`; Render supplies a separate `https://YOUR_SERVICE.onrender.com` API address. Both platforms manage HTTPS certificates. Replace these illustrative names with addresses allocated to your accounts.

These files are preparation templates. Local validation does not create accounts or services, publish images, deploy remotely, or push Git commits. Importing a Blueprint or connecting a deployment integration later can create resources immediately; do so only when ready to publish.

```text
Browser ──HTTPS── Cloudflare Pages (static assets and React Router)
   └──────HTTPS── Render (public read-only API, Java 21 / Docker)
                     └──verified PostgreSQL TLS── Neon (direct endpoint)
Local operator ──────verified PostgreSQL TLS──────┘
```

The [self-hosted Compose alternative](deployment-compose.md) remains available. Its Nginx proxy, traffic limits, and private-network assumptions do not apply to this split deployment.

## 1. Accounts and service names

Choose the **Free** plan on all three platforms, a Render/Neon region pair with low latency, and a unique Pages project name. Keep automatic deployments and previews disabled initially. No custom domain, paid cron, persistent Render disk, paid database, or Pages Function is required. If the allocated Pages name changes, update the exact backend CORS origin before publishing.

A purchased domain is unnecessary: Pages allocates `https://<assigned-project>.pages.dev` and manages its TLS certificate. Render separately allocates `https://<assigned-service>.onrender.com`. These addresses have not been allocated/verified for this repository yet; do not treat illustrative names as deployed URLs. The repository is [lngabriel/atlasglobal](https://github.com/lngabriel/atlasglobal).

| Address | Status / purpose |
| --- | --- |
| [Neon project console](https://console.neon.tech/app/projects/silent-cherry-24123415/) | Existing verified project. |
| `https://<assigned-project>.pages.dev` | Frontend and exact backend CORS origin, pending Pages project allocation. |
| `https://<assigned-service>.onrender.com` | API origin for `VITE_API_BASE_URL`, pending Render service allocation. |
| `https://<assigned-service>.onrender.com/healthz` | Minimal platform readiness probe after deployment. |
| `https://<assigned-service>.onrender.com/api/docs` | Public API documentation after deployment. |

## 2. Neon PostgreSQL

Use the existing project **`silent-cherry-24123415`**, organization **`org-frosty-boat-57690161`**, production branch **`production`**. Authenticated metadata identifies region **`aws-sa-east-1` (São Paulo)** and PostgreSQL **18**. Do not create a replacement project or recreate its existing database. The local Compose database remains PostgreSQL 17; the backend suite also passes against isolated PostgreSQL 18.

1. In the existing project's **Connect** dialog, disable **Connection pooling** and select the intended existing database/role.
2. Keep the direct host, database, role, and password private. The hostname ends in `.neon.tech`; its endpoint name must not have the `-pooler` suffix.
3. Retain Free scale-to-zero and inspect storage, compute, and transfer usage before large imports. Do not enable Neon Auth, Data API, or other products for this JDBC application.

The [official Neon setup prompt](https://neon.com/get-started/prompt.md) describes CLI authentication and agent setup. This repository uses its existing-project path, with separate [project skills and OAuth MCP configuration](development-tools.md). It links metadata with `--no-env-pull` to preserve local configuration instead of overwriting `.env` through initialization.

After authenticating the official CLI, fetch the required private backend fields without printing a connection string:

```bash
python3 scripts/configure_neon.py \
  --project-id silent-cherry-24123415 \
  --org-id org-frosty-boat-57690161
```

The helper verifies organization ownership, requests the direct read/write endpoint with `verify-full`, and creates `infrastructure/free-tier/backend.env` atomically with mode `600`. It refuses existing files and non-ignored destinations. It does not change database data or print credentials. `--neon-cli` and `--config-dir` select an isolated CLI installation/profile directory; `--branch-id`, `--database-name`, and `--role-name` select an existing resource when needed.

If the Pages address is already allocated, pass `--frontend-origin https://YOUR_PROJECT.pages.dev`. Otherwise the helper deliberately omits CORS; add the exact allocated origin privately before starting the backend. It never invents a production address. A real JDBC read-only connection has been verified, but no remote schema migration or application deployment has been performed.

Atlas uses session-level PostgreSQL advisory locks to coordinate imports. Transaction pooling does not preserve those locks, so a small direct connection pool is used for the application, Flyway, imports, and backup/restore. See [Neon pooling limitations](https://neon.com/docs/connect/connection-pooling).

Neon's direct endpoint is reachable over the internet but requires database credentials and verified TLS; only the backend and trusted operational tools receive those credentials. The Free plan does not include [IP Allow](https://neon.com/docs/introduction/ip-allow), which currently requires Scale. No paid network feature is enabled and no IP allowlist/private network is claimed. Keep the database out of frontend configuration and use the existing application database/role without opening additional services.

The `render` Spring profile constructs this JDBC URL from separate fields:

```text
jdbc:postgresql://DIRECT_HOST:5432/DATABASE?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=10&socketTimeout=60&tcpKeepAlive=true
```

`verify-full` checks encryption, the certificate chain, and hostname using the Java trust store. Do not substitute `sslmode=require`, disable certificate checks, embed passwords in URLs, or override `SPRING_DATASOURCE_URL` / `SPRING_FLYWAY_URL`. See [Neon TLS](https://neon.com/docs/connect/connect-securely) and [JDBC SSL semantics](https://jdbc.postgresql.org/documentation/ssl/).

Flyway applies/validates migrations at backend startup; Hibernate validates the schema. A fresh database contains no statistical observations. Initial imports run separately and never invent absent values.

The existing Flyway dependency is pinned to **11.20.3**, whose [PostgreSQL module supports version 18](https://github.com/flyway/flyway/blob/flyway-11.20.3/flyway-database/flyway-database-postgresql/src/main/java/org/flywaydb/database/postgresql/PostgreSQLDatabase.java), while retaining Spring Boot 3 and Java 21. This removes the older Boot-managed Flyway 11.7.2 compatibility warning. The same migration files are tested on local PostgreSQL 17 and 18; no migration history was rewritten.

## 3. Render backend

Use [render.yaml](../render.yaml) as a reviewed Blueprint or create an equivalent **Web Service** manually. The template defines one `plan: free` service and `autoDeployTrigger: off`; it creates no Render database or paid job. Choose the region near Neon before creation, since it cannot be changed in place.

| Setting | Value |
| --- | --- |
| Runtime / language | Docker |
| Repository branch | `main` |
| Docker build context | `backend`, relative to the repository root |
| Dockerfile | `backend/Dockerfile`, relative to the repository root |
| Root directory | Leave empty with the paths above |
| Compute plan | **Free**, 512 MB RAM / 0.1 CPU |
| Health check path | `/healthz` |
| Auto-deploy | **Off** |
| Docker command override | Leave empty; retain the existing entrypoint |

Render can build from the existing Dockerfile when you later request publication; a separately published registry image is not required. Pulling an existing immutable image is optional. The image runs as a non-root user and contains no runtime database credentials. [Docker deployment](https://render.com/docs/docker), [Blueprint specification](https://render.com/docs/blueprint-spec).

Enter these **backend-only** environment variables privately in Render:

| Variable | Expected value | Origin |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `render`; required for the deployment boundary. | Spring Boot / repository. |
| `DB_HOST` | Direct Neon hostname, without scheme, port, path, or `-pooler`. | Private Neon connection fields. |
| `DB_NAME` | Existing Neon database name. | Private Neon connection fields. |
| `DB_USERNAME` | Existing Neon role. | Private Neon connection fields. |
| `DB_PASSWORD` | Actual password entered privately in the dashboard. | Neon secret; never versioned. |
| `CORS_ALLOWED_ORIGINS` | Exactly `https://YOUR_PROJECT.pages.dev`; no trailing slash, wildcard, preview, port, or path. | Allocated Cloudflare Pages production URL. |
| `JAVA_TOOL_OPTIONS` | Recommended bounded JVM settings below; Blueprint supplies them. | Repository / JVM. |
| `PORT` | Supplied automatically by Render; fallback `10000`. Do not set a host Compose port. | Render platform. |

Supported optional variables are listed below. Keep schedules disabled on the Free web service and leave the import-once variable false; imports run as local operational processes.

| Variable | Expected/default value | Origin |
| --- | --- | --- |
| `CACHE_ENABLED` | `true`. | Application configuration. |
| `WORLD_BANK_SCHEDULE_ENABLED` | `false`. | Application configuration; keep disabled on Free. |
| `ADDITIONAL_SOURCES_SCHEDULE_ENABLED` | `false`. | Application configuration; keep disabled on Free. |
| `POLICY_RATE_SCHEDULE_ENABLED` | `false`. | Application configuration; keep disabled on Free. |
| `WORLD_BANK_IMPORT_CRON` | `0 0 3 * * SUN`, UTC; inactive while disabled. | Application configuration. |
| `ADDITIONAL_SOURCES_IMPORT_CRON` | `0 0 5 * * SAT`, UTC; inactive while disabled. | Application configuration. |
| `POLICY_RATE_IMPORT_CRON` | `0 0 4 * * *`, UTC; inactive while disabled. | Application configuration. |
| `WORLD_BANK_COUNTRIES` | Default 24 WDI-supported home codes, excluding Taiwan; exact list below. | Operator-selected import scope. |
| `WORLD_BANK_FROM_YEAR` | `1960`. | Operator-selected annual history start. |
| `WORLD_BANK_TO_YEAR` | `0`, meaning current UTC year. | Operator-selected annual history end. |
| `POLICY_RATE_FROM` | `2010-01-01`. | Operator-selected dated history start. |
| `WORLD_BANK_IMPORT_ONCE` | `false`; `true` imports and exits, so never enable it on the Render web service. | Local operations only. |

The default country list is `ARG,AUS,BRA,CAN,CHL,CHN,FRA,HKG,ISR,ITA,JPN,PRY,POL,PRT,RUS,SGP,KOR,ESP,CHE,UKR,GBR,USA,VEN,VNM`. Source-specific imports still enforce their documented coverage.

```text
-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss256k -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError
```

The heap ceiling leaves space for native memory, metaspace, thread stacks, and the JVM. It does not guarantee every workload fits in 512 MB. [application-render.yml](../backend/src/main/resources/application-render.yml) bounds Tomcat and Hikari: at most three direct database connections, zero minimum idle, thirty-second idle eviction, and no pool keepalive query.

Render supplies `PORT`; the profile binds to `0.0.0.0:${PORT}` with a 10000 fallback. Keep Spring datasource/Flyway/CORS overrides and management overrides unset. Startup rejects missing required variables, pooled hosts, an unsafe datasource override, or an invalid production frontend origin. Do not add credentials as Docker build arguments: Render also exposes service variables during builds, and the Dockerfile deliberately does not consume secret arguments.

`DATABASE_URL` from Neon is not consumed by this Java application, and `JAVA_OPTS` is not consumed by the Docker entrypoint. Convert the connection into the existing `DB_*` fields; use `JAVA_TOOL_OPTIONS` for JVM options. Do not add invented aliases to the panel.

The following existing local/test variables are not production panel settings:

| Variable | Local purpose | Render behavior |
| --- | --- | --- |
| `DB_URL` | Default-profile JDBC URL, normally localhost. | Replaced by the strict direct Neon URL. |
| `SERVER_PORT` | Default-profile Java port, normally `8080`. | Replaced by Render's `PORT`. |
| `MANAGEMENT_ADDRESS`, `MANAGEMENT_PORT` | Local management overrides. | Fixed loopback `127.0.0.1:8081`; unsafe Spring overrides fail validation. |
| `DB_PORT`, `BACKEND_PORT`, `FRONTEND_PORT` | Local Compose published ports. | Not consumed by the deployed backend. |
| `ATLAS_BASE_URL`, `ATLAS_BROWSER_WAIT_MS`, `CHROME_BIN`, `ATLAS_SCREENSHOT_DIR` | Local browser tests. | Not application runtime inputs. |

### Health checks and cold starts

`GET /healthz` returns only `{"status":"UP"}` when Spring accepts traffic, otherwise HTTP 503, with `Cache-Control: no-store`. This inexpensive readiness probe does not query Neon on every platform check; that could prevent database suspension. Flyway verifies database access during startup. The existing **private loopback** `/actuator/health/readiness` includes PostgreSQL; its management port is not published. Data requests still fail safely when the database is unavailable.

Render Free sleeps after fifteen minutes without incoming traffic and takes about a minute to resume. External frontend API requests default to a bounded ninety-second timeout, retain one transient query retry, and explain slow startup after ten seconds. CSV uses the same timeout and a manual retry after failure. Cancellation still stops obsolete requests. There is no synthetic keepalive traffic, unlimited retry loop, or continuous-availability guarantee. [Render Free](https://render.com/docs/free), [health checks](https://render.com/docs/health-checks).

### Public API boundary

The browser calls Render directly. Backend CORS permits the exact production Pages origin, GET/HEAD/OPTIONS, and no credentials. CORS does not prevent independent non-browser clients from accessing a public API. Backend responses retain safe errors, CSP, frame/MIME protection, bounded input/pagination, and gzip. OpenAPI and Swagger are available on **Render** at `/api/openapi` and `/api/docs`.

The existing **Nginx API/CSV burst limits do not apply** to the direct Render URL. Pages headers protect static assets, not API traffic. No managed rate-limiting service is assumed free or configured. Monitor public usage and quotas; CORS is not a traffic limit. No public import/administration endpoint is added.

## 4. Cloudflare Pages frontend

Create a **Pages** project for the existing repository, not a Worker or full-stack framework project. Use the following settings; see [React setup](https://developers.cloudflare.com/pages/framework-guides/deploy-a-react-site/) and [build configuration](https://developers.cloudflare.com/pages/configuration/build-configuration/).

The [official Cloudflare agent setup prompt](https://developers.cloudflare.com/agent-setup/prompt.md) covers developer tooling. It does not allocate a domain or configure this application's build automatically. This project needs only static Pages hosting; it does not require a Worker, Pages Function, additional application package, or custom domain.

| Setting | Value |
| --- | --- |
| Production branch | `main` |
| Root directory | `frontend` |
| Build command | `npm ci && npm run build:pages` |
| Build output directory | `dist` |
| `NODE_VERSION` | `22.23.4` |
| `SKIP_DEPENDENCY_INSTALL` | `1`; the build command runs reproducible `npm ci`. |
| `VITE_API_BASE_URL` | Exact HTTPS Render **origin**, e.g. `https://YOUR_SERVICE.onrender.com`; no `/api` suffix. |
| `VITE_API_TIMEOUT_MS` | Optional, default `90000` externally; range `15000`–`120000`. |

All `VITE_*` values are public build inputs. Put **no** passwords, tokens, private database URLs, or backend environment files in Pages. The API setting is only its public origin. Changing the origin/timeout requires a new frontend build. `build:pages` rejects an absent or invalid API origin; normal `npm run build` preserves same-origin local/Docker behavior when `VITE_API_BASE_URL` is unset.

The build emits `dist/_headers` with a CSP permitting only the exact API origin and the frontend origin in `connect-src`, frame/MIME/referrer/permissions protections, and immutable caching for hashed assets. HTML retains Pages' default revalidation; local geography has its MIME type and a one-day cache. JSON, annual CSV, policy-rate CSV, and the API documentation link use the same origin.

Pages' built-in SPA fallback preserves React Router deep links when no top-level `404.html` exists. Do not add a catch-all API rewrite or an unnecessary Pages Function. `/api` is not a backend proxy on Pages. Invalid app routes retain the UI's own not-found screen. See [SPA serving](https://developers.cloudflare.com/pages/configuration/serving-pages/), [static headers](https://developers.cloudflare.com/pages/configuration/headers/), and [Node version selection](https://developers.cloudflare.com/pages/configuration/build-image/).

Disable automatic production branch deployments and set preview branch deployments to **None** initially. Production CORS intentionally excludes preview/branch/hash URLs; do not expand it to `*.pages.dev`. A future preview needs its own isolated backend/environment. [Branch deployment controls](https://developers.cloudflare.com/pages/configuration/branch-build-controls/).

## 5. Initial data and refreshes

Render Free has no shell, one-off jobs, persistent disk, or reliable always-running scheduler. The `render` profile defaults in-process import schedules to disabled; keep their variables false on Free. Run initial imports and later refreshes from a trusted local machine using the existing application and a private environment file:

```bash
# Only if the private file does not already exist:
test -e infrastructure/free-tier/backend.env || \
  cp infrastructure/free-tier/backend.env.example infrastructure/free-tier/backend.env
chmod 600 infrastructure/free-tier/backend.env
# Edit the private file locally; never commit it or copy it to Pages.
docker build -t atlas-global-backend:local ./backend
docker run --rm --env-file infrastructure/free-tier/backend.env \
  atlas-global-backend:local --atlas.import.run-once=true
```

The World Bank command populates supported country metadata and observations. Run the other one-shot commands from the [README](../README.md#import-world-bank-data) with the same `docker run --rm --env-file ... IMAGE` prefix. Use one import command per process, preserve source-specific definitions, and bound countries/years to the desired scope. Local CLI containers can have more memory than the free web service and write directly to Neon while Render sleeps. No HTTP import endpoint is needed.

Refreshes are manual in this setup. A local OS scheduler can run the commands while the operator's machine is awake; that is not a platform-provided scheduler. Do not promise daily refreshes from a sleeping Render process. Monitor import audit outcomes and retain source/reference-date disclosures.

## 6. Backup and restoration

Install a PostgreSQL client matching or newer than the server major version. Copy [pg_service.conf.example](../infrastructure/free-tier/pg_service.conf.example) **outside the repository**, replace its values, and configure an absolute private passfile path and trusted CA bundle path. On Debian/Ubuntu, the system bundle is normally `/etc/ssl/certs/ca-certificates.crt`. Unlike the Java driver, native libpq needs an explicit CA location or an appropriately configured default certificate store.

The private passfile contains one entry:

```text
DIRECT_HOST:5432:DATABASE:ROLE:YOUR_PRIVATE_PASSWORD
```

Set both private files to mode `600`. Escape literal `:` and `\` in fields according to [PostgreSQL passfile rules](https://www.postgresql.org/docs/17/libpq-pgpass.html). Use the **direct** Neon endpoint and verified TLS. See [Neon backup and restore](https://neon.com/docs/manage/backup-pg-dump).

```bash
export PGSERVICEFILE="$HOME/.config/atlas-global/pg_service.conf"
export PGSERVICE=atlas-global
scripts/backup.sh --remote "backups/atlas-$(date -u +%Y%m%dT%H%M%SZ).dump"
```

The script forces verified TLS, creates a private custom-format dump atomically, refuses existing output paths, and removes partial output on failure. libpq reads credentials from the passfile, not command arguments. Keep an encrypted independent copy; Git ignores `backups/`. Neon's short Free restore history is not a substitute for tested dumps.

For a drill, create an empty **separate** database and a second private service entry, such as `atlas-restore-check`, selecting that database. Then run:

```bash
pg_restore --dbname='service=atlas-restore-check sslmode=verify-full' \
  --no-owner --no-acl --exit-on-error backups/YOUR_VERIFIED_BACKUP.dump
psql 'service=atlas-restore-check sslmode=verify-full' -X -v ON_ERROR_STOP=1 \
  -c 'SELECT count(*) FROM flyway_schema_history WHERE success;'
```

Verify migrations, annual/world/policy-rate counts, uniqueness, and representative source values. The temporary restore also consumes Free storage. A drill does not replace the live database; production recovery requires an explicit maintenance/cutover plan.

## 7. Manual publication and updates

When ready to publish:

1. Review the exact commit and wait for GitHub CI after your own authorized push. CI does not deploy.
2. Configure Neon and Render privately, manually deploy the reviewed backend commit, and inspect Flyway logs, `/healthz`, and a real API query. Render supplies HTTPS.
3. Deploy Pages with the assigned Render origin. Confirm HTTPS at its `pages.dev` address and that CORS accepts exactly that frontend.
4. Import data locally, then check country profiles, histories, comparisons, CSV, map navigation, and refreshed deep links. Allow for both platforms' cold starts.
5. Review browser requests/CSP/CORS, management isolation, actual memory and usage, and spending/auto-deployment controls. Never silently select paid resources to bypass limits.

For updates, back up Neon, identify frontend/backend commits and build inputs, run CI, then manually deploy backend and frontend. A frontend rollback must still target a compatible API. Backend rollback requires schema compatibility; Flyway does not reverse migrations. Preserve data and migration history during recovery.

The optional manual GHCR workflow remains available but is unused by the Render-from-Dockerfile path. Pages requires no registry token.

## Free-plan limits and pending remote verification

These official limits were reviewed on **2026-11-10** and can change. Recheck dashboards before publication; no continuous availability or unlimited usage is promised.

| Platform | Relevant Free limits | Consequence |
| --- | --- | --- |
| [Cloudflare Pages](https://developers.cloudflare.com/pages/platform/limits/) | 500 builds/month; one concurrent build; twenty-minute build timeout; 20,000 files/site; 25 MiB/file. | Publish static `dist` only; no Functions/Worker quota is assumed. |
| [Render](https://render.com/docs/free) / [compute](https://render.com/docs/compute-plans) | 512 MB / 0.1 CPU; fifteen-minute idle sleep; roughly one-minute resume; 750 instance-hours/workspace/month. No free shell, one-off jobs, persistent disk, or always-on guarantee. Separate bandwidth/build allowances and external-database/API traffic restrictions apply. | Bounded JVM, startup-aware UI, local operations. Review spending controls and avoid paid overages. |
| [Neon](https://neon.com/docs/introduction/plans) | 1 GB PostgreSQL storage/project; 100 CU-hours/project/month; 5 GB public transfer/project/month; mandatory five-minute idle suspension; instant restore up to six hours / 1 GB changes. | Small direct pool, bounded imports, independent backups; no paid scheduled snapshots or SLA assumed. |

Local validation covers PostgreSQL 18 migrations/JPA/API tests, builds, certificate/hostname enforcement with an isolated authority, CORS, health, constrained-container startup, and application flows. A real JDBC connection to the existing Neon project was verified with hostname/certificate validation and a read-only transaction; no remote DDL was executed. Pages/Render allocated addresses, platform-edge behavior, true Render cold starts, provider egress eligibility, account quotas, and sustained production memory remain publication checks. No remote deployment is claimed.
