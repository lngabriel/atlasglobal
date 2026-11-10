# Local infrastructure

Services are defined in the root [docker-compose.yml](../docker-compose.yml). The build contexts are `backend/` and `frontend/`; each contains a Dockerfile and a `.dockerignore` file.

- PostgreSQL 17 Alpine: persistent volume and health check.
- Backend: Maven/JDK 21 build and a non-root JRE 21 runtime.
- Frontend: Node.js 22 build and a non-root Nginx runtime with an SPA routing fallback.
- The Actuator port is neither published nor forwarded by the proxy.
- All published ports are bound to `127.0.0.1`.

See the main [README](../README.md) for configuration variables and commands. The local configuration does not include public hosting or TLS.

The separate [production Compose configuration](production/compose.yml) runs verified container images with private database/backend networking, non-root read-only application containers, and readiness checks. Its frontend is bound to loopback for the selected HTTPS ingress. Configure its private environment file and exact trusted proxy addresses before exposure; see the [deployment reference](../docs/deployment.md).
