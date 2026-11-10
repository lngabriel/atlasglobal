# IMF DataMapper response fixtures

Captured from the official DataMapper v2 API on 2026-11-10. The selected indicators belong to **World Economic Outlook (October 2026)**, with a native `projection-year` of 2026. Production imports request the real endpoints; these files are used only by contract tests.

- `indicators.json`: the original metadata for `GGXWDG_NGDP` and `BCA_NGDPD`, reduced from `/api/v2/indicators`.
- `countries.json`: original identities for Brazil, the United States, Taiwan, Kosovo, and Venezuela, reduced from `/api/v2/countries`.
- `government-debt.json`: original response metadata and selected country/aggregate observations for 2023–2028 from `/api/v2/GGXWDG_NGDP/BRA/USA/CHN/TWN?periods=2023,2024,2025,2026,2027`.
- `current-account.json`: original response metadata and selected country/aggregate observations for 2023–2028 from `/api/v2/BCA_NGDPD?periods=2024,2025,2026`.

Both responses returned observations beyond the requested periods and countries. Fixtures retain some of these extra rows to verify local scope filtering. No values or metadata were fabricated. Controlled mutations for zero, missing values, malformed responses, and edition changes occur only inside tests.

Official references: [API documentation](https://www.imf.org/external/datamapper/api/) and [WEO dataset methodology](https://www.imf.org/external/datamapper/datasets/WEO). The metadata's `last-modified` timestamp is not treated as an observation-level statistical revision date. Historical WEO values can include IMF staff estimates; records before `projection-year` are not labeled as final actuals.
