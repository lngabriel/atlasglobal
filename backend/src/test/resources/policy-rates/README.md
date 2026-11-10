# Official response fixtures

Captured on 2026-11-10 for provider contract tests. Production imports use the real APIs.

- `bis-daily.json`: BIS SDMX REST v2, WS_CBPOL1.0 daily data, SDMX-JSON1.0.0; date constraint `c[TIME_PERIOD]=ge:2026-10-01+le:2026-11-10`. Original structure, units, instrument metadata and daily observations retained.
- `nbu-daily.json`: National Bank of Ukraine `NBUStatService/v1/statdirectory/key?json`, reduced to actual source rows for 2026-11-01 through 2026-11-10. Policy and overnight facility IDs retained to test instrument selection.

Documentation: https://stats.bis.org/api-doc/v2/ and https://bank.gov.ua/en/open-data/api-dev .
