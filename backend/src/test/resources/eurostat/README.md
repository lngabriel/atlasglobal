# Eurostat response fixtures

Captured from the official Eurostat Statistics API on **2026-11-10**. Production imports request the real APIs and never load these fixtures.

- `debt.json`: `gov_10dd_edpt1`, annual (`freq=A`) general government (`sector=S13`) consolidated gross debt (`na_item=GD`) in percent of GDP (`unit=PC_GDP`), for 2023–2025. The requested country list is `FR,ES,PT,IT,PL,EL,UK`; the response contains six countries and excludes the unavailable UK selection.
- `renewables.json`: `sdg_07_40`, annual (`freq=A`) overall renewable energy (`nrg_bal=REN`) in percent (`unit=PC`), for the same requested countries and periods. Original three-decimal values and provisional (`p`) flags are retained.
- `renewables-empty.json`: the same definition with `geoLevel=country&sinceTimePeriod=2026&untilTimePeriod=2026`. The official HTTP 200 response has an empty time dimension and no value cells, which must remain a successful empty history.

The JSON-stat structure, native values, status flags, and dataset annotations are unchanged. Controlled malformed, zero, negative, missing, dense-array, and permuted-dimension cases are generated only in tests. Dataset `updated`/`UPDATE_DATA` dates do not establish observation-level statistical revision dates.

Official documentation: [Statistics API guide](https://ec.europa.eu/eurostat/en/web/user-guides/data-browser/api-data-access/api-getting-started/api), [government debt metadata](https://ec.europa.eu/eurostat/cache/metadata/en/gov_10dd_esms.htm), and [renewable-energy metadata](https://ec.europa.eu/eurostat/cache/metadata/en/sdg_07_40_esmsip2.htm).
