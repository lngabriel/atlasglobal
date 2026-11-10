# Policy rates

`POLICY.RATE` is a dated economic measure in percent per year. It has its own daily observation model and public query endpoints; it does not change the existing annual indicator definitions or convert daily rates into annual averages. The interface is available at `/juros`, from country profiles, and from the comparison workspace.

## Official sources

| Scope | Integrated source and instrument |
| --- | --- |
| Brazil | [Banco Central do Brasil, SGS 432](https://dadosabertos.bcb.gov.br/dataset/432-taxa-de-juros---meta-selic-definida-pelo-copom): the Copom's **Meta Selic**, not the effective overnight Selic. |
| Other covered countries | [BIS central bank policy rates](https://data.bis.org/topics/CBPOL), dataflow `BIS:WS_CBPOL(1.0)`, daily keys `D.{REF_AREA}`. |
| Ukraine | [National Bank of Ukraine](https://bank.gov.ua/NBUStatService/v1/statdirectory/key?json), selecting only `KEY_PolicyRate`. The overnight deposit and lending facilities returned by the same endpoint are excluded. |

The [BIS compilation reference](https://www.bis.org/statistics/cbpol/cbpol_doc.pdf) explains national instrument changes and target-band treatment. A target, repo rate, discount rate, and target-band midpoint are not identical instruments. The adapter retains the provider's instrument, source organization, compilation notes, and historical breaks. For example, a US target-band midpoint retains three-decimal precision rather than being substituted with the upper bound. The euro-area series retains the BIS's historical instrument changes, including the deposit facility rate used since September 2024.

The BIS's daily observation frequency is separate from its publication schedule, which is normally weekly. BCB and BIS can consequently have different latest dates on the same collection day. Neither an HTTP timestamp nor a collection timestamp establishes a policy-decision date or statistical revision date.

### Country scope and gaps

The importer uses `atlas.import.countries` (`WORLD_BANK_COUNTRIES`), independently of annual year bounds. Its default is the 24 home-selection codes supported by WDI. Import the country catalog first. Existing observations outside the configured scope are retained.

For Brazil, only the BCB series is stored; the BIS Brazilian series is not used to overwrite it. Ukraine uses the NBU fallback. Other countries match the BIS reference-area ISO2 code. France, Italy, Portugal, and Spain in the default selection use the published euro-area series `D.XM`, with an explicit shared-instrument note. The mapping also supports the other 1999 monetary-union members if included in the operational scope. Euro-area observations before 1999 are excluded. Later euro adopters are not automatically mapped without an accession-specific definition.

The default selection can obtain validated historical series for 20 countries and territories, including 19 with observations in 2026 in the checked release. Argentina's BIS history ends in 2025: the source states that it stopped adopting a policy interest rate in July 2025 and moved to monetary-aggregate control. The original historical observations are retained, with an explicit notice that their latest value is not a current 2026 rate. Paraguay, Singapore, Taiwan, Venezuela, and Vietnam remain unavailable in this integration. Official sources were investigated; their absence is not represented by zero:

| Country | Official source reviewed and limitation |
| --- | --- |
| Argentina | The BIS history is retained through its final published record. [BCRA APIs](https://www.bcra.gob.ar/apis-banco-central/) and the [v4 monetary-variable reference](https://www.bcra.gob.ar/Catalogo/Content/files/pdf/principales-variables-v4.pdf) were also checked. Deposit and interbank rates are not used to create a current policy-rate substitute. |
| Paraguay | [Banco Central del Paraguay](https://www.bcp.gov.py/web/institucional/home) publishes the TPM, but a reliable automated historical contract has not been integrated. A current value shown on a web page is not hardcoded into the application. |
| Singapore | [MAS monetary policy framework](https://www.mas.gov.sg/monetary-policy/Singapores-Monetary-Policy-Framework/faqs/section-2) targets the exchange rate. SORA is not substituted for an interest-rate target. |
| Taiwan | [Central Bank of the Republic of China](https://www.cbc.gov.tw/en/mp-2.html) publishes discount-rate decisions. Its event dates are not expanded into invented daily observations; a historical adapter remains unvalidated. |
| Venezuela | [Banco Central de Venezuela interest-rate statistics](https://www.bcv.org.ve/estadisticas/tasas-de-interes) could not be validated through the available HTTPS connection. Certificate validation is preserved. |
| Vietnam | [State Bank of Vietnam](https://www.sbv.gov.vn/) publishes several distinct policy instruments. A current, automated historical definition has not been validated. |

## Import and refresh

After building the backend and importing the country catalog:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.policy-rates.run-once=true
```

The default interval starts on `2010-01-01` and ends on the current UTC date. Configure `POLICY_RATE_FROM` or command arguments for a different initial interval. An end date in the future is rejected. Only dates actually returned within the requested bounds are accepted.

To retry one provider or import a bounded interval:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.policy-rates.run-once=true \
  --atlas.policy-rates.providers=NBU \
  --atlas.policy-rates.from=2026-01-01 \
  --atlas.policy-rates.to=2026-11-10
```

Provider codes are `BCB`, `BIS`, and `NBU`; a comma-separated subset is accepted. The fixed dates above are an interval example, not fixed observation values. The policy-rate one-shot flag is mutually exclusive with all other import commands. All schedules are suppressed in any one-shot process. Imports require no API credentials and expose no public write endpoint.

Compose enables periodic policy-rate updates by default: `POLICY_RATE_SCHEDULE_ENABLED=true`, with `POLICY_RATE_IMPORT_CRON=0 0 4 * * *` at **04:00 UTC daily**. Local Java execution defaults to scheduling disabled. A scheduled refresh rereads the last 45 days, bounded by the configured initial date and end date. Run the full command periodically to capture older source revisions. Enabling the schedule does not perform an immediate initial historical import.

Executions reuse `data_import` audits and provider-specific PostgreSQL advisory locks. BCB policy and BCB household-debt imports share a source lock. A source failure is recorded and reported with a nonzero one-shot exit code; other providers can still finish. Historical dates are not deleted on failure. Metadata, observation upserts, and progress commit together per country definition; reruns update existing `(series_id, observation_date)` keys without duplicates. This retains historical periods, rather than every vintage of revised values.

## Transport and normalization

- BCB requests SGS 432 in bounded chunks of at most ten years. NBU requests at most one year at a time to avoid large historical-response failures.
- BIS uses the [SDMX REST v2 API](https://stats.bis.org/api-doc/v2/) with `c[TIME_PERIOD]=ge:YYYY-MM-DD+le:YYYY-MM-DD` and `Accept: application/vnd.sdmx.data+json;version=1.0.0`. Legacy `startPeriod`/`endPeriod` parameters are not used on this v2 route.
- Responses validate dates, dimensions, units, identifiers, duplicate keys, and duplicate observation dates. BIS dimension indices are resolved explicitly; the time dimension is not assumed to be ordered. Multipliers are applied with exact decimal arithmetic.
- BIS `NaN` with missing status `M` and upstream nulls become explicit `MISSING` records. Numeric zero and negative rates remain available values. Absent dates generate no rows.
- Connect/read timeouts, bounded retries, bounded decompressed responses, and strict JSON decoding apply. Permanent HTTP errors are not repeatedly requested; excessive `Retry-After` values stop the attempt for a later operational retry.
- Original reference dates, native daily frequency, and precision are preserved. Unknown statistical revision dates remain null, separately from `importedAt`.

## Public presentation

The latest-value summary displays each country's own date, source, instrument metadata, and collection timestamp. Histories use ISO dates, a one-year default, custom periods up to twenty years, and one to five countries. The chart and table align actual dates without carrying forward an earlier value, interpolating missing observations, or converting frequencies. The most recent date with numeric observations for all selected countries is identified separately from each country's latest value.

CSV export retains full ISO dates, exact decimals, daily frequency, instrument, source, provider status, and provenance. See the [API reference](../api.md#policy-rates) for routes and fields. Annual comparisons and annual rankings continue to use their existing source-specific series and year rules.
