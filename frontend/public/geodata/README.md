# World map geometry

`countries-110m.geojson` is derived from Natural Earth's Admin 0 countries at 1:110 million scale. It is a simplified navigation layer, not a statistical source or a complete representation of every small country and territory.

- Source: https://github.com/nvkelso/natural-earth-vector/blob/master/geojson/ne_110m_admin_0_countries.geojson
- Downloaded: 2026-11-09
- SHA-256 of the original response: `6866c877d39cba9c357620878839b336d569f8c662d3cfab4cb1dbe2d39c977f`
- License: public domain. https://www.naturalearthdata.com/about/terms-of-use/

The committed derivative preserves every original geometry and retains only `ADM0_A3`, `ISO_A3`, `ISO_A3_EH`, `WB_A3`, `NAME`, and `NAME_PT`. The original file's population, GDP, income classifications, and other statistical attributes are intentionally excluded. All values shown by Atlas Global come from its persisted statistical API.

Matching uses an exact existing catalog code, preferring Natural Earth's ISO identifiers, with World Bank and administrative identifiers as fallbacks. The legacy Natural Earth World Bank identifier `KSV` is matched to the current World Bank catalog identity `XKX` for Kosovo (https://api.worldbank.org/v2/country/XKX?format=json). Features without a matching catalog identity remain unselectable. This layer does not add country identities, infer statistical coverage, or classify sovereignty.

The complete country selector remains available independently of the simplified geometries. The application serves this file locally and does not load external map tiles or tracking assets.
