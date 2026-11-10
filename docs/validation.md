# Validation results

Verified on **2026-11-10** with Java 21.0.13, Maven 3.9.12, PostgreSQL 17.11, Node.js 22.23.4, Google Chrome, and Docker Engine 29.9.3. These results describe a tested local snapshot; official providers can revise values and coverage.

## Backend

```bash
mvn -f backend/pom.xml -Pintegration verify
```

**166 backend tests passed** — 107 unit/contract tests and 59 PostgreSQL integration tests, with no failures, errors, or skipped tests. Integration tests used a dedicated database; transactional store tests rolled back their writes and cache tests removed their committed audit fixtures.

Coverage includes:

- Explicit CORS, safe errors, read-only API methods, OpenAPI, and isolated Actuator health endpoints.
- Bounded catalog caching, immutable pagination keys, invalidation after successful or failed imports committed by another connection, and cache bypass inside existing snapshot transactions.
- Registered MVC handlers and view beans exclude the SSE fragment and XSLT rendering features covered by the reviewed Spring advisories.
- World Bank pagination, metadata, aggregate exclusion, exact decimals, null versus zero, HTTP retries, invalid provider responses, and bounded import configuration.
- OECD annual dimension selection, RFC 4180 CSV, exact unit multipliers, missing values, zero, duplicate rejection, distinct price bases, retries, and transport dates that must not become statistical revision dates.
- IBGE metadata, variables, national geography, classifications, annual periods, coefficient scale, price-base identity, exact decimals, SIDRA symbols, bounded gzip, duplicate keys, and HTTP failures.
- BCB strict monthly dates, exact December selection, complete-year boundaries, precision, missing values, duplicates, retries, and catalog prerequisites.
- Flyway migrations, JPA mappings, transactional upserts, idempotency, retained history, incompatible series rejection, per-provider import coordination, and one-shot scheduling isolation.
- Country/source filters, stable pagination, source-specific histories, separate world observations, national-summary coverage, and server-side rejection of direct national-poverty and Brazil-only comparisons. Every pair of one-shot commands is rejected before execution. Provider spies confirm that public queries do not contact external sources.
- Rankings: one source and reference year, numeric coverage, missing versus zero/negative values, tied positions, stable pagination, empty histories, validation, and blocked national comparisons.
- CSV: every history page, exact decimals, quoted multiline text, formula-like text prefixing, explicit missing rows, no fabricated absent years, provenance, selection validation, and comparison-policy enforcement.
- Daily rates: strict BIS SDMX dimensions and instrument metadata, native dates, negative/zero/missing rates, NBU instrument selection and annual request chunk boundaries, BCB SGS 432, HTTP/JSON errors, bounded retries, full-date CSV, transactional idempotent persistence, retained history, source failure auditing, euro-area mapping, national-source priority, discontinued Argentine history, unchanged annual rows, and scheduling isolation.
- IMF: DataMapper v2 metadata and country validation, exact decimals, current-year projection flags, historical estimates, WEO release identity, Taiwan/Kosovo mappings, bounded local filtering, metadata consistency, and controlled provider errors.
- UNDP: official publication discovery, Windows-1252 transport, report-edition identity, HDI/IHDI reference columns, native 0–1 values, blank cells, strict CSV, country/aggregate exclusion, encoding/size limits, retries, and unavailable country coverage.
- Eurostat: explicit JSON-stat dimensions, dataset identity, flattened sparse/dense cells, empty current-year intervals, exact decimals, native provisional flags, country mappings, unsupported geographies, and malformed responses.
- Shared annual orchestration: catalog prerequisites before upstream calls, source-specific locking, audit failures, idempotency, retained periods, duplicate/out-of-scope rejection, six-year refresh bounds, and projection metadata in public history/ranking/CSV responses. All eight one-shot commands suppress every schedule.

## Frontend and browser

```bash
cd frontend
npm test
npm run build
npm run test:browser
```

**72 frontend unit tests passed**, with no failures or skipped tests. TypeScript and the Vite production build passed. Unit coverage includes complete paginated retrieval, cancellation, safe errors, localized units, absent-year gaps, zero versus missing values, annual changes, country search, comparison selection, and latest common-year calculations. National tests preserve three-decimal Gini values and changes, dynamic real-income price bases, and the distinct BCB denominator. Additional contracts cover rankings, CSV transport, local geography, dated rates, common dates, projection/provisional labels, three-decimal HDI/renewable values, and absolute index changes. Inconsistent units, countries, or failed intermediate pages reject the complete history rather than exposing partial data.

**79 Chrome checks passed** against the production application served by Nginx at `http://127.0.0.1:3000`, with the documented annual and policy-rate imports loaded:

- The home directory contains exactly the 25 selected countries and territories, with direct profile links and no emoji flags. Selection search works.
- All four global highlights match persisted `WLD` values, individual reference years, and source metadata.
- The complete explorer retains search, region/income filters, category filtering, pagination, shared URLs, and individual profiles.
- Annual histories, period presets, custom single-year intervals, keyboard chart tooltips, accessible tables, and persistent themes work.
- Loading and controlled network failures do not display stale charts; retry restores the history.
- Two-to-five-country comparisons use one series and a common reference year. Displayed values and annual tables match the real API.
- Gini shows its methodological caveat. National-poverty comparison is blocked without a history request. Unavailable Taiwan observations remain unavailable.
- The OECD wage profile matches its actual annual PPP value, source, and reported constant-price base; its unknown statistical update date remains null.
- All three national histories match their actual imported values, scales, years, units, and sources. IBGE income retains its published price base. National comparison remains blocked without a history request and offers Brazil's individual profile.
- Rankings match API values, reference years, coverage, order, and absolute positions across pages; invalid national comparisons do not request ranking data.
- Annual country and comparison CSV files are downloaded and parsed, with their rows checked against the real API rather than only checking that a button responds.
- Daily policy rates match persisted BCB/BIS values and precision; historical tables, five-year filters, and full-date CSV downloads work. The NBU fallback identifies Ukraine's actual source. Singapore does not substitute SORA for a policy target, and Argentina's discontinued history carries an explicit notice.
- IMF projection labels appear in country summaries, historical tables, comparisons, rankings, and downloaded CSV. Taiwan's native IMF data is available while its WDI observations remain unavailable.
- UNDP profiles match actual API values, their reference years, the 0–1 scale, and three-decimal CSV values. Eurostat renewable profiles and exports preserve native precision and provisional flags.
- The world map selects countries through real polygons, keyboard activation, and the full selector; headline values match persisted API data. Zoom and reset work; microstate selection and unavailable observations remain usable without external tile requests.
- Home, comparison, and explorer layouts have no page-level horizontal overflow at 1440, 768, 390, and 320 pixels. Mobile country selection preserves visible keyboard focus.
- Unknown countries/routes and countries without observations show their corresponding recoverable states. No JavaScript exceptions, content policy violations, or unexpected API errors occurred.

Desktop and mobile screenshots were visually reviewed in light and dark themes, including the world map, rankings, and dated-rate views. New routes were checked at 768, 390, and 320 pixels without page-level horizontal overflow. Backend and frontend Docker images built successfully; their builds execute unit tests before packaging.

The fresh-install smoke suite also passed **nine Chrome checks** against the separate production Compose runtime, without statistical imports. It covers the selected country directory/search, Taiwan's seeded profile, map selection, information page, responsive widths, and absence of browser/content-policy errors.

## Delivery, security, and operations

- HTTP checks passed for security headers, private management endpoints, gzip, GeoJSON MIME type, versioned-bundle caching, HTML revalidation, and Nginx health. A separate loopback burst test verified HTTP 429 with `Retry-After`, recovery after the limit interval, and rejection of spoofed forwarded-IP identities.
- The npm audit reported **zero vulnerabilities**. The resolved Java audit checked **91 runtime coordinates**, with **zero unreviewed findings** and two exact, time-limited Spring Web MVC assessments. These are usage-specific findings, not patched library advisories; see [security](security.md) and the required review date of **2026-12-10**.
- All **three dependency-audit tool tests** passed, including incomplete-provider-response rejection and assessment expiry. `actionlint` validated the CI and manual image-publication workflows.
- The isolated production stack reached healthy status. Backend/frontend run as non-root users with read-only filesystems and dropped capabilities. Only the loopback frontend port is published; database, backend, and management ports remain private. Provider schedules are disabled by default in this configuration.
- A fresh-database backup restored with its seeded identity and all five Flyway migrations. A separate backup of the populated local database restored into an isolated verification database with **43,918 annual observations, 111,586 daily policy-rate observations, and 264 world observations**. The source database was not modified by backup or restore.

CI and manual GHCR publication are configured and locally validated. No GitHub workflow was executed remotely, images were not published, and no public deployment was performed. Hosting, domain, TLS, and registry access remain deployment inputs. See [deployment and recovery instructions](deployment.md).

## Live imports and database integrity

The application database contains completed World Bank and OECD imports. Both new national commands also completed with exit code 0 and persisted `COMPLETED` execution records. Read-only database checks found:

| Stored records | Count |
| --- | ---: |
| Country/economy identities | 218 |
| Indicator definitions | 36 |
| Source-specific annual series | 36 |
| Country observations | 43,918 |
| Numeric country observations | 26,235 |
| Explicitly missing country observations | 17,683 |
| Published world observations | 264 |
| Dated policy-rate series | 20 |
| Daily policy-rate observations | 111,586 |
| Numeric daily policy-rate observations | 101,689 |
| Explicitly missing daily policy-rate observations | 9,897 |

All country/series/year keys and world series/year keys are unique. Previously imported Germany and India histories remain present, with 528 records each. Taiwan has native IMF observations and no invented WDI or UNDP observations.

The OECD execution imported **1,420 records**: 487 average-wage records across 14 selected countries, 601 minimum-wage records across 13, and 332 household-debt records across 13. The wage response reported a 2025 constant-price base, preserved in the stored unit and series identity. All OECD statistical update dates remain null because the response does not establish a validated revision date; independent collection timestamps are present.

The IBGE command imported **28 observations**: 14 annual Gini coefficients and 14 annual real monthly household-income values, covering 2012–2025. The BCB command imported **21 December observations**, covering completed years 2005–2025. Their latest values matched the official responses: Gini 0.511, monthly household income per person R$ 2,264 at 2025 prices, and December household debt 49.70% of restricted RNDBF. See the [Brazilian source reference](integrations/brazil.md).

Annual national observations exist only for Brazil, with no invented statistical revision dates. The original 40,541 annual observations, including 39,072 World Bank and 1,420 OECD records, were retained. The 264 world records remain separate, and all current country/series/year keys are unique.

The dated-rate commands persisted **6,158 BCB records**, **99,270 BIS records across 18 country definitions**, and **6,158 NBU records**. BCB and NBU cover `2010-01-01` through `2026-11-10`; the latest BIS observation in this import is `2026-11-03`, with country-specific dates retained. Argentina's native BIS history ends on `2025-07-10`, and the source documents that no policy rate was adopted thereafter. No current rate is fabricated for that country. The NBU's first large-response attempt failed without persisted rows; bounded yearly requests then completed successfully, retaining both audit outcomes.

The additional annual imports completed with exit code 0 and `COMPLETED` audits, using the 25 home-selection codes and a 1960–2026 interval:

| Provider | Persisted observations | Detail |
| --- | ---: | --- |
| IMF | 1,960 | 830 government-debt and 1,130 current-account records, including 73 native Taiwan records. The October 2026 WEO edition has 49 current-year projections and 1,911 historical records; historical does not assert final actual data. |
| UNDP | 1,152 | 816 HDI and 336 IHDI cells from HDR 2025; 1,144 numeric values and eight explicit missing cells. HDI covers 1990–2023 and IHDI 2010–2023 in this publication. Taiwan is absent. |
| Eurostat | 265 | 155 government-debt records (1995–2025) and 110 renewable-energy records (2004–2025), for France, Italy, Poland, Portugal, and Spain. The Swiss geographic category has no value cells in the checked energy response. |

These imports added **3,377 annual observations** without changing the 39,072 WDI, 1,420 OECD, 28 IBGE, or 21 annual BCB records. World and daily policy-rate counts remain unchanged. All 43,918 annual keys are unique, no future observation year is imported, and all three new providers retain null statistical revision dates rather than treating publication/HTTP maintenance dates as observation updates. The new opt-in Saturday 05:00 UTC schedule refreshes the last six calendar years.

Policy-rate series/date keys are unique, no observation is later than the current UTC date, and annual/world observation counts remain unchanged. Unknown statistical revision dates remain null. The default Compose policy-rate refresh is enabled at 04:00 UTC daily; it refreshes the last 45 days and does not replace the initial historical import.

The application Compose services remain available. The isolated integration-test database is stopped after verification. Operational readiness includes PostgreSQL connectivity; management endpoints remain private.
