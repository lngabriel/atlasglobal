# Official UNDP response fixture

Captured on 2026-11-10 from the current official [Human Development Report downloads](https://hdr.undp.org/data-center/documentation-and-downloads).

- Download: `https://hdr.undp.org/sites/default/files/2025_HDR/HDR25_Composite_indices_complete_time_series.csv?download=1`.
- Original response: 2,001,263 bytes, Windows-1252 encoding, SHA-256 `61ed82e5b66c88dfca8ff9fac775c63981ecab6a254862af97acacc41c143117`.
- The fixture retains actual country metadata and all native HDI/IHDI annual columns for Brazil, the United States, Hong Kong, Côte d'Ivoire, North Korea, and the world aggregate. Other rows and unrelated index columns are omitted. The shortened fixture uses UTF-8 and LF line endings for repository review; transport tests encode it back to Windows-1252 to verify the original download contract.
- Blank cells are preserved. Tests that inject zero, an out-of-range value, changed headers, or duplicate records are controlled validation scenarios, not production data.

The official DataPRISM API specification requires an API key; this integration uses the public CSV linked by UNDP instead. The original UNdata SDMX catalog does not expose HDI. Production imports discover the current published CSV link from the official documentation and never load test fixtures.
