# United Nations human development indices

Atlas Global imports the Human Development Index (HDI) and Inequality-adjusted Human Development Index (IHDI) published by the **United Nations Development Programme (UNDP)**. The source code is `UNDP`, identifying the responsible UN agency rather than treating all United Nations datasets as one interchangeable provider.

## Official publication and access

The [UNdata API manual](https://data.un.org/Host.aspx?Content=API) describes its SDMX service. Its [dataflow catalog](https://data.un.org/ws/rest/dataflow) was reachable during validation, but did not expose an HDI dataflow. That service is not used to imply HDI coverage it does not publish.

UNDP's [documentation and downloads](https://hdr.undp.org/data-center/documentation-and-downloads) links both the DataPRISM API and the public composite-index CSV. The [DataPRISM OpenAPI specification](https://prism.hdrdata.org/openapi/v1.json) requires an `X-API-Key` obtained through registration. Atlas Global instead uses the official publicly downloadable CSV linked by UNDP; no API key or registration is required for this adapter.

On 2026-11-10, the documentation linked:

```text
https://hdr.undp.org/sites/default/files/2025_HDR/HDR25_Composite_indices_complete_time_series.csv?download=1
```

The verified response contained 206 rows and 1,112 columns, with HDI reference years 1990–2023 and IHDI reference years 2010–2023. The report edition is **2025**, while the latest reference year is **2023**. These are different concepts. The [official HDI page](https://hdr.undp.org/data-center/human-development-index) announced the next report for December 2026; an announcement does not create published 2026 observations.

Each import discovers the latest linked composite file from the fixed official documentation page. Only the expected UNDP composite-file path pattern is accepted, the report year must match its filename suffix, and the request is constructed against the fixed `hdr.undp.org` host. Missing or inconsistent publication links fail the import rather than silently reverting to an older release. The dataset identity includes the edition (`HDR:{edition}`), so a new report cannot overwrite or splice values into an earlier edition's series.

The current CSV is Windows-1252, not UTF-8; its original encoding is decoded explicitly. Documentation HTML uses UTF-8. The client uses an identifying Atlas Global user agent and the verified download query, preserving HTTPS certificate validation. A permanent access failure is recorded instead of bypassing provider access controls.

## Definitions and interpretation

| Internal indicator | Category | Native field | Unit |
| --- | --- | --- | --- |
| `UNDP.HDI` | Demography | `hdi_{year}` | `INDEX_0_1` |
| `UNDP.IHDI` | Inequality | `ihdi_{year}` | `INDEX_0_1` |

The [HDI definition](https://hdr.undp.org/data-center/human-development-index) combines normalized health, education, and gross national income dimensions using a geometric mean. It describes average human development and does not directly measure inequality, poverty, or every aspect of wellbeing.

The [IHDI definition](https://hdr.undp.org/inequality-adjusted-human-development-index) discounts those dimensions for inequality in their distributions. It equals HDI in the absence of inequality and falls below it as inequality rises. It is neither a Gini coefficient nor the percentage loss attributed to inequality. Survey timing and coverage differ, and IHDI is available for fewer countries than HDI. The [2025 technical notes](https://hdr.undp.org/sites/default/files/2025_HDR/HDR25_Technical_Notes.pdf) describe both indices, their underlying source estimates, and their methodological limits.

Atlas Global stores the published decimal indices on their original **0–1 scale**. It does not recompute components, rescale them to percentages, derive IHDI from HDI, estimate missing values, or interpolate years. Official composite indices can incorporate modelled estimates and revisions from UNDP and its underlying sources; they are not represented as raw survey observations produced by Atlas Global.

## Normalization and coverage

- The CSV must contain country metadata columns and native annual HDI/IHDI columns. Gender-specific indices, rank columns, other composite indices, and similarly named components are not substituted for the selected definitions.
- Country identities match the configured existing ISO3 catalog codes. Regional and human-development-group aggregates have different identifiers and are excluded. No country identity or metadata is inferred from an aggregate.
- Taiwan is absent from the verified UNDP download. Its existing Atlas Global catalog identity does not cause an observation to be invented.
- A published blank annual cell becomes an explicit `MISSING` observation. A numeric zero remains `AVAILABLE`; values outside the 0–1 scale or unrecognized nonnumeric tokens fail validation. A country absent from the source creates no rows.
- Only actual annual columns are interpreted as reference periods. Configured year bounds filter those published periods; they do not generate additional years. Duplicate country records and malformed CSV headers or quoting are rejected.
- The download has no validated observation-level statistical revision dates or per-cell status flags. Consequently, `sourceUpdatedAt` and `providerStatus` remain null. HTTP resource dates are not substituted for statistical revision dates, and collection timestamps are stored independently.

Historical comparisons, rankings, and CSV exports use one explicit source-specific series and report edition. Choosing one common year does not eliminate differences in the surveys and estimates underlying the index. A later report can revise historical values; those editions remain separate and independently selectable.

## Operations and resilience

Import the country catalog first, then run the internal command:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.un-import.run-once=true
```

The command uses the shared `atlas.import.countries`, `atlas.import.from-year`, and `atlas.import.to-year` settings. It only writes observations for known countries in that operational scope and native source years within the selected interval. A request extending beyond the latest published index year remains bounded by the available source columns.

The adapter normalizes data before the common annual import coordinator persists it. Import executions retain audit status, transactional metadata/observation batches, idempotent upserts, and a provider-specific PostgreSQL advisory lock. Public requests read persisted PostgreSQL data and never trigger downloads or imports. Repeating a command updates matching observations without duplicating the series/country/year key; historical periods outside the selected interval remain intact.

Requests have ten-second connection and forty-five-second read timeouts. Response sizes and decompressed gzip payloads are limited to five million bytes. At most three attempts are made for connection failures, HTTP 429, or HTTP 5xx. Permanent HTTP errors are not retried; a provider-requested delay longer than one minute stops the execution for a later operational retry. HTTP payloads are not included in public errors or persisted failure messages.

## Verified response and tests

The 2025 edition's 2023 records contained HDI/IHDI values of **0.786/0.594 for Brazil**, **0.938/0.832 for the United States**, and **0.955/0.839 for Hong Kong**. These are a dated source snapshot, not fixed values in application code.

Provider contract tests use a reduced official fixture with all native HDI/IHDI annual columns for selected countries and the world aggregate. They verify edition identity, exact decimals, native reference years, blank cells, aggregate exclusion, absent-country coverage, strict value/header validation, the Windows-1252 download contract, publication-link discovery, retries, permanent access errors, and response limits. Production imports use the live official publication, never the fixture.
