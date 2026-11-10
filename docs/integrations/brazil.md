# Brazilian national sources

Atlas Global imports selected national statistics directly from IBGE and Banco Central do Brasil (BCB). They complement international coverage without replacing, converting, or splicing World Bank/OECD definitions. Public API requests read PostgreSQL; they never initiate provider requests.

## Scope and operational commands

Run the World Bank import first to populate the country catalog, then run each national import in a separate process:

```bash
docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.ibge-import.run-once=true \
  --atlas.import.countries=BRA

docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --atlas.bcb-import.run-once=true \
  --atlas.import.countries=BRA
```

Both use `atlas.import.from-year` and `atlas.import.to-year`. The configured country list must include `BRA`; additional codes are ignored by the national adapters, which write only Brazilian observations. Neither provider requires an API key. All eight one-shot flags described in the [root README](../../README.md) are mutually exclusive, and any one-shot command suppresses every scheduled import. National annual imports are manual and do not run during normal startup.

Each provider has its own PostgreSQL advisory lock and import-history records. Sources, definitions, and observations use the shared transactional store and existing schema. Repeating an import updates the same source/series/year keys without duplicating records. A failed run retains completed batches and sanitized failure history; periods outside the requested interval are not deleted.

## IBGE PNAD Contínua annual statistics

The [IBGE Aggregates API v3](https://servicodados.ibge.gov.br/api/docs/agregados?versao=3) exposes SIDRA table metadata and selected observations. The source code is `IBGE`.

| Internal indicator | SIDRA selection | Stored identity and unit |
| --- | --- | --- |
| `IBGE.GINI.HOUSEHOLD.PER.CAPITA` | Table `7435`, variable `10681`, national geography `N1=1`, no additional classification | Dataset `SIDRA_7435`, external code `10681`, unit `GINI_COEFFICIENT`. |
| `IBGE.HOUSEHOLD.INCOME.PER.CAPITA` | Table `7533`, variable `10816`, national geography `N1=1`, classification `1019`, category `49243` (`Total`) | Dataset `SIDRA_7533:{baseYear}`, external code `10816.C1019.49243`, unit `BRL_REAL_{baseYear}_MONTHLY_PER_PERSON`. |

The original tables describe:

- [Table 7435](https://sidra.ibge.gov.br/tabela/7435): Gini coefficient of monthly household income per capita, at average prices of the reference year. The coefficient remains on its published **0–1 scale** and is displayed with three decimal places. It is not converted to the WDI 0–100 index.
- [Table 7533](https://sidra.ibge.gov.br/tabela/7533): mean real monthly household income per person, from all income sources, at average prices of the last published year. This is an annual survey statistic with a monthly monetary unit. It does not measure employee wages, annual remuneration, or purchasing-power-parity income.

These publications consolidate first household visits for 2012–2019 and from 2023, and fifth visits for 2020–2022. Survey design, coverage, and revisions remain relevant to interpretation. The values are official survey estimates published by IBGE; Atlas Global produces no estimates of its own.

### Request and validation

For each table, the importer requests `/api/v3/agregados/{table}/metadados` and the complete published annual history:

```text
https://servicodados.ibge.gov.br/api/v3/agregados/7435/periodos/all/variaveis/10681?localidades=N1[1]
https://servicodados.ibge.gov.br/api/v3/agregados/7533/periodos/all/variaveis/10816?localidades=N1[1]&classificacao=1019[49243]
```

Metadata validation checks the expected annual PNAD survey, table, variable, source unit, national geography, and selected classification. Response validation checks the same variable and unit, Brazil's national geography, annual periods, and the exact total-population category where required. An unexpected definition, unit, classification, or geography fails the import instead of changing the measure silently.

The real-income table states that its values use average prices of the last published year. The importer reads that year from `periodicidade.fim` and verifies that it matches the latest returned annual period. It fetches the complete history before applying the configured year filter, so requesting an older interval does not assign an incorrect older price base. A later price base creates a separate dataset/unit identity; values from different bases are never merged into one series.

IBGE responses use decimal strings and [published data symbols](https://www.ibge.gov.br/explica/producao-agropecuaria/pam/):

| Published cell | Stored observation |
| --- | --- |
| Numeric decimal string, including `0` | Exact decimal, `AVAILABLE`. |
| `-` | Absolute zero: numeric zero, `AVAILABLE`, original symbol retained in `providerStatus`. |
| `..`, `...`, or `X` | Null value, `MISSING`, original symbol retained in `providerStatus`. |

Unknown symbols, malformed numbers, out-of-range coefficients, and duplicate JSON keys are rejected. Response bodies and gzip decompression are bounded. No missing cell is replaced by an estimate, and no absent year is synthesized.

## Banco Central household indebtedness

The source code is `BCB`. The adapter uses [SGS series 29037](https://dadosabertos.bcb.gov.br/dataset/29037-endividamento-das-familias-com-o-sistema-financeiro-nacional-em-relacao-a-renda-acumulada-dos): household indebtedness with the Brazilian financial system relative to income accumulated over twelve months.

| Field | Definition |
| --- | --- |
| Internal indicator | `BCB.HOUSEHOLD.DEBT.DECEMBER` |
| Dataset | `SGS` |
| External code | `29037.DECEMBER` |
| Unit | `PERCENT_HOUSEHOLD_RNDBF_12M` |
| Country | Brazil (`BRA`) |
| Annual reference | The source's actual December observation for each completed calendar year. |

The denominator is **restricted gross national disposable household income (RNDBF)** accumulated over the preceding twelve months. The restricted definition excludes gross operating surplus and property income. Consult the [BCB methodological explanation](https://www.bcb.gov.br/content/estatisticas/hist_estatisticasmonetariascredito/202112_Texto_de_estatisticas_monetarias_e_de_credito.pdf).

This debt-to-income ratio is neither the percentage of indebted households nor debt as a share of GDP. Its denominator differs from the OECD net-disposable-income measure. The current RNDBF-based series contains revised history from 2005; the discontinued MSAD-based SGS series `19882` is not spliced into it.

The provider endpoint is fixed to the selected series:

```text
https://api.bcb.gov.br/dados/serie/bcdata.sgs.29037/dados?formato=json&dataInicial=01/01/2020&dataFinal=31/12/2025
```

Requests use bounded intervals and return monthly records containing `data` and `valor`. The client validates the collection, strict monthly reference dates, interval boundaries, duplicates, and exact decimal strings. Only December records are stored, with the corresponding calendar year. A missing December record does not cause another month to be substituted or a new annual observation to be synthesized. The importer stops at the latest completed calendar year even if a current-year monthly value is available. It calculates neither an annual mean nor a forecast.

## Provenance, visibility, and comparisons

IBGE and BCB catalog-maintenance or HTTP resource dates do not establish an observation-level statistical revision date. Consequently, `sourceUpdatedAt` remains null when the provider does not supply a validated revision date; `importedAt` independently records collection time.

National definitions appear in Brazil's country summaries and the complete indicator catalog. They are excluded from other countries' summary rows. An explicit single-country history outside Brazilian coverage returns no observations rather than fabricated national values.

Both sources publish `comparison.allowed: false` with a coverage/methodology note. Multi-country requests return HTTP 409, while Brazilian historical charts and accessible tables remain available. The interface does not offer a comparison link from a blocked national profile series and directs blocked national comparison views to Brazil's profile.

## Verified provider snapshot

Live source responses checked on **2026-11-09** reported:

| Selected measure | Latest applicable reference | Published value |
| --- | --- | ---: |
| IBGE Gini coefficient | 2025 | 0.511 |
| IBGE real monthly household income per person | 2025, at 2025 average prices | R$ 2,264 |
| BCB household indebtedness, December | December 2025 | 49.70% |

These are source-response snapshots, not guaranteed future latest values. Publication schedules and historical revisions can change; normal queries do not refresh the providers.
