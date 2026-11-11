# Neon development tools

This repository includes the official `neon` and `neon-postgres` agent skills under `.agents/skills/`. Their upstream source is [neondatabase/agent-skills](https://github.com/neondatabase/agent-skills); the Neon entry skill matches the [official published skill](https://neon.com/.well-known/agent-skills/neon/SKILL.md). The upstream Apache 2.0 license is retained in `.agents/skills/LICENSE.neon`.

The project-scoped Codex configuration in `.codex/config.toml` registers the official Neon MCP endpoint with OAuth, read-only tools, and the existing Atlas project ID. It stores no API key, password, or connection string. The global Codex configuration and unrelated MCP servers are not replaced. Restart the Codex session to load a newly registered server; MCP may request its own OAuth sign-in on first use. Installing a server is distinct from completing its OAuth connection.

For terminal operations, use an authenticated official Neon CLI. A local CLI installation and credentials may be kept under the ignored `.tools/` directory; do not commit that directory or display its credentials. A project context in `.neon` contains only organization, project, and branch identifiers and is also ignored.

Link an existing project without changing local database settings:

```bash
neon link --org-id YOUR_ORGANIZATION_ID --project-id YOUR_EXISTING_PROJECT_ID \
  --no-env-pull --no-config -y
```

Avoid an implicit environment pull over a local `.env`. Keep deployment credentials in the separate ignored operational file described in [deployment](deployment.md). Existing Spring Boot, JPA, Hibernate, Flyway, direct-connection import locks, and public API behavior remain the application architecture. These development tools do not enable Neon Auth, Data API, Functions, or other backend products, and do not deploy anything.

Use the skills for the relevant task and verify current official guidance before applying changes. Do not replace the installed files or unrelated configuration without reviewing the upstream changes. Skills become available on the next interaction; MCP configuration is loaded when the session starts.
