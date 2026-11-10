# Security and dependency maintenance

Atlas Global provides public read-only queries. It has no accounts, sessions, authentication middleware, or public administrative operations. Operational imports run as commands or configured schedules. Database credentials remain in environment variables and are not sent to the frontend.

## HTTP boundary

The Nginx frontend is the public application entry point. It applies:

- A shared-memory per-client API limit of ten requests per second, with a burst of 100. CSV endpoints additionally allow two requests per second, with a burst of five. Excess requests return HTTP 429 and `Retry-After: 1`; OPTIONS requests are excluded from these read limits.
- A one-megabyte request-body limit, bounded upstream connection/read timeouts, and no public Actuator proxy.
- Content Security Policy, frame denial, MIME sniffing protection, and a referrer policy. Swagger's inline-style exception is scoped to its own UI route.
- Gzip for public text/JSON assets, long-lived caching of versioned bundles, daily revalidation of local geographic data, and HTML revalidation on each visit.

Rate limiting uses the actual connection address. Forwarded IP headers are untrusted by default. A deployment behind an ingress must configure only that ingress's verified address/CIDR in `trusted-proxies.conf`, and the ingress must replace untrusted incoming forwarding headers. Trusting arbitrary forwarded headers would let clients choose their own rate-limit identity. See [deployment](deployment.md).

The production Compose file exposes only a loopback frontend port. Backend, PostgreSQL, and Actuator have no published host ports. Public TLS belongs to the selected ingress. Backend and frontend containers run without root privileges or capabilities, use read-only root filesystems and temporary writable space, and have health checks. CORS uses exact configured origins without credentials.

Input validation, parameterized queries, bounded pagination/date ranges, source-specific comparison rules, and safe problem responses are enforced by the backend. CSV text handling and complete-download behavior are described in the [API reference](api.md#csv-export). Provider adapters retain fixed official upstream hosts, TLS validation, timeouts, response limits, and bounded retries.

## Dependency audit

The backend remains on Java 21 and Spring Boot 3. The following compatible security overrides were verified on **2026-11-10**:

| Component | Version |
| --- | --- |
| Jackson BOM | 2.21.8 |
| Tomcat | 10.1.61 |
| PostgreSQL JDBC | 42.7.14 |
| Log4j API bridge/BOM | 2.25.5 |
| Commons Lang | 3.21.0 |

The frontend audit covers the npm lockfile. The Java audit resolves runtime coordinates and queries the public [OSV API](https://google.github.io/osv.dev/api/). It fails on unreviewed advisories or an incomplete audit. The inventory contains public package names and versions only; no application data or credentials are sent.

Two findings for Spring Web MVC 6.2.19 have a reviewed **not affected by current application usage** assessment:

| Advisory | Applicability evidence |
| --- | --- |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | Requires SSE view-fragment rendering. No such endpoint is registered; public queries return JSON/CSV. |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | Requires XSLT view rendering under the described mapping conditions. Neither `XsltView` nor its resolver is registered. |

`FoundationIT` verifies the registered rendering surface. The exact package/version/advisory assessments are stored in [.github/advisory-assessments.json](../.github/advisory-assessments.json) and require review by **2026-12-10**. They do not suppress other versions or future advisories. Adding view rendering or SSE requires reassessing these findings.

The upstream advisories list the patched 6.2 release as enterprise-only; the public fixed major is Spring Framework 7. The project retains the requested Spring Boot 3 architecture. A future framework migration or supported maintenance release is a separate compatibility decision; the audit does not claim those two library advisories have disappeared.

Run the reproducible checks with:

```bash
cd frontend
npm ci
npm audit --audit-level=high
cd ..
mkdir -p .tools
mvn -B -f backend/pom.xml dependency:list -DincludeScope=runtime -DoutputFile="$PWD/.tools/maven-runtime.txt"
python3 scripts/audit_maven.py .tools/maven-runtime.txt
```

CI also performs dependency review for pull requests. Dependabot proposes weekly Maven, npm, Docker, and GitHub Actions updates. Workflow actions are pinned to verified release commits, checkout does not persist credentials, and write permission is limited to the manually dispatched image-publication job. A passing scan is a dated assessment rather than a guarantee about future findings.

## Query cache

Spring Cache and Caffeine cache small public catalogs and the world overview. Each cache is limited to 256 entries with a sixty-second lifetime; large histories, rankings, and CSV bodies are not cached. Cache keys preserve search filters, pagination, and an import-audit version.

Each key evaluation keeps a small PostgreSQL audit read. Started and completed import timestamps and execution count invalidate entries across JVMs, including successful and failed command-line imports. This avoids relying on an in-process eviction event that another import container cannot send. Metadata committed during an active import can remain cached until completion or expiry; unknown manual database changes are bounded by the same lifetime. `QueryCacheIT` verifies reuse, pagination isolation, and refresh after another connection commits an import outcome. Set `CACHE_ENABLED=false` to disable application caching.

Cache advice runs before a standalone service's transaction advice. An already active transaction bypasses cached catalog entries, so rankings and exports retain metadata from their own repeatable-read snapshot rather than an earlier cached response. The cache integration tests cover this boundary explicitly.
