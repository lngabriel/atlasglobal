-- Identity only: IMF Data Mapper country catalog, https://www.imf.org/external/datamapper/api/v1/countries
-- WDI does not publish TWN. No region, capital, coordinates, or observations are inferred.
INSERT INTO country (id, iso2_code, iso3_code, name, original_name)
VALUES ('adacb035-aed7-43dc-a6ab-34db19989b21', 'TW', 'TWN', 'Taiwan', 'Taiwan Province of China')
ON CONFLICT (iso3_code) DO NOTHING;
