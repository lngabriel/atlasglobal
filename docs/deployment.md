# Deployment and operations

The repository includes a portable Docker Compose deployment and manual container-image publication workflow. Platform, domain, public TLS, and registry access are deployment inputs. No hosting account, remote environment, or GitHub publication is created by local validation.

## CI and image publication

[CI](../.github/workflows/ci.yml) runs on pull requests, main-branch pushes, and manual dispatch. It checks Java 21 with isolated PostgreSQL integration tests, frontend tests/build/audit, resolved Java dependencies, Docker builds, and fresh-database browser/HTTP smoke checks. Statistical provider imports are disabled in CI; the full live-data browser suite runs separately against an imported local database.

The [image-publication workflow](../.github/workflows/release-images.yml) is manual, restricted to `main`, and requires a successful CI push run for the exact commit. It publishes backend/frontend images to GHCR with commit tags and reports immutable image digests. It uses the workflow token with package-write permission; no personal registry token is stored in the repository. Dispatching this workflow publishes images but does not deploy a site.

After approving a release, select **Publish container images** in GitHub Actions and run it on the verified main commit. Record both digests from the job summaries. Registry visibility/access must match the target host; private package pulls require host-side registry authentication.

## Configure the runtime

```bash
cp infrastructure/production/.env.example infrastructure/production/.env
chmod 600 infrastructure/production/.env
```

Set a unique database password, exact public HTTPS CORS origin, and verified image digests in the private file. The production configuration does not publish database/backend ports and binds the frontend to host loopback for a trusted TLS ingress. The frontend and backend have read-only root filesystems, temporary writable directories, non-root users, and health checks. The database volume remains persistent.

```bash
docker compose --env-file infrastructure/production/.env \
  -f infrastructure/production/compose.yml config --quiet
docker compose --env-file infrastructure/production/.env \
  -f infrastructure/production/compose.yml pull
docker compose --env-file infrastructure/production/.env \
  -f infrastructure/production/compose.yml up -d --wait
```

The default project name is `atlas-global-production`, separate from the local development database. Flyway validates/applies migrations on startup. A fresh installation has country identity metadata but no imported statistical observations. Use the operational commands in the root README with the same `--env-file` and `-f` options; initial imports are not triggered by page visits. All production schedules default to disabled. Enable them after loading the desired historical scope; the scope, dates, and cron variables documented in the README are forwarded to the backend. Container logs rotate at ten megabytes with three retained files per service.

## TLS and trusted proxy addresses

The selected ingress must terminate HTTPS and forward traffic to the private frontend. For example, a host-installed Caddy reverse proxy can use:

```caddyfile
atlas.example.com {
    reverse_proxy 127.0.0.1:3000
}
```

This is an ingress example, not a configured domain. DNS and access to the certificate challenge ports must be established for the chosen platform. Follow its [reverse-proxy and forwarded-header documentation](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#defaults).

Configure [trusted-proxies.conf](../infrastructure/production/trusted-proxies.conf) only after verifying the ingress address seen by Nginx. A host reverse proxy commonly appears as the Docker network gateway; inspect the actual deployment network rather than copying the illustrative address:

```bash
docker network inspect atlas-global-production_default \
  --format '{{range .IPAM.Config}}{{.Gateway}}{{end}}'
```

Trust that exact verified address, set `real_ip_header X-Forwarded-For`, and enable recursive processing only for the controlled proxy chain. The ingress must sanitize incoming forwarding headers. Without this configuration, rate limiting safely uses the connection address, which can group visitors behind one ingress. With a managed ingress, use its documented source addresses and adapt the loopback-port arrangement to its container routing model.

Restart the frontend after changing its trusted-proxy configuration so Nginx reloads the verified addresses.

Verify HTTPS, redirects, CSP, public API access, client-IP handling, and management-port isolation before exposing the service. Health and operational checks remain available inside containers:

```bash
docker compose --env-file infrastructure/production/.env \
  -f infrastructure/production/compose.yml exec backend \
  wget -qO- http://127.0.0.1:8081/actuator/health/readiness
```

`/healthz` checks Nginx only; backend readiness includes PostgreSQL and remains on the private management port. Health responses do not expose credentials or internal component details.

## Backup and restore

Create a custom-format PostgreSQL backup before release changes:

```bash
scripts/backup.sh "backups/atlas-$(date -u +%Y%m%dT%H%M%SZ).dump" \
  --env-file infrastructure/production/.env -f infrastructure/production/compose.yml
```

The script uses private file permissions, refuses an existing output path, and publishes the backup only after a successful dump. `backups/` and environment files are ignored by Git. Store copies outside the host and regularly verify restoration. The dump contains data and schema, including import history; external secret configuration requires its own protected backup.

Restore into a new empty database for verification:

```bash
docker compose --env-file infrastructure/production/.env \
  -f infrastructure/production/compose.yml exec -T postgres \
  sh -c 'createdb -U "$POSTGRES_USER" atlas_restore_check'
docker compose --env-file infrastructure/production/.env \
  -f infrastructure/production/compose.yml exec -T postgres \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d atlas_restore_check --no-owner --no-acl' \
  < backups/your-verified-backup.dump
```

Check row counts, migrations, and source coverage in the restored database before planning a production recovery. Recovery of the live database requires a maintenance window and an explicit database cutover; the verification command above does not replace it.

## Upgrade and rollback

Keep a record of the deployed commit, both image digests, environment configuration, and a verified backup. Replace the image references, pull, and run `up -d --wait`; check readiness and representative public queries before routing traffic. Keep operational imports disabled during a migration/cutover when a stable snapshot is required.

Rolling back images is valid only when the prior application is compatible with the current schema. Flyway migrations are not reversed automatically. If a schema change prevents rollback, restore a verified backup into a new database and plan the cutover rather than rewriting migration history or deleting the existing volume.
