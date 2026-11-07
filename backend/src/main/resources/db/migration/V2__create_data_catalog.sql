CREATE TABLE country (
    id UUID PRIMARY KEY,
    iso2_code VARCHAR(2) NOT NULL UNIQUE,
    iso3_code VARCHAR(3) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    original_name VARCHAR(200) NOT NULL,
    region VARCHAR(200),
    income_level VARCHAR(200),
    capital VARCHAR(200),
    latitude NUMERIC(10,6),
    longitude NUMERIC(10,6),
    CONSTRAINT country_iso2 CHECK (iso2_code ~ '^[A-Z0-9]{2}$'),
    CONSTRAINT country_iso3 CHECK (iso3_code ~ '^[A-Z0-9]{3}$'),
    CONSTRAINT country_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT country_longitude CHECK (longitude BETWEEN -180 AND 180)
);
CREATE TABLE data_source (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    base_url TEXT NOT NULL,
    description TEXT NOT NULL,
    documentation_url TEXT NOT NULL
);
CREATE TABLE indicator (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(40) NOT NULL
);
CREATE TABLE indicator_series (
    id UUID PRIMARY KEY,
    indicator_id UUID NOT NULL REFERENCES indicator(id),
    data_source_id UUID NOT NULL REFERENCES data_source(id),
    external_code VARCHAR(100) NOT NULL,
    dataset_code VARCHAR(40) NOT NULL,
    original_name TEXT NOT NULL,
    methodology TEXT NOT NULL,
    source_organization TEXT NOT NULL,
    unit VARCHAR(100) NOT NULL,
    frequency VARCHAR(20) NOT NULL CHECK (frequency = 'ANNUAL'),
    UNIQUE (data_source_id, dataset_code, external_code)
);
CREATE TABLE indicator_observation (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL REFERENCES country(id),
    series_id UUID NOT NULL REFERENCES indicator_series(id),
    period INTEGER NOT NULL CHECK (period BETWEEN 1800 AND 2200),
    value NUMERIC,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'MISSING')),
    provider_status VARCHAR(40),
    source_updated_at DATE,
    imported_at TIMESTAMPTZ NOT NULL,
    UNIQUE (country_id, series_id, period),
    CONSTRAINT observation_value_status CHECK (
        (status = 'AVAILABLE' AND value IS NOT NULL) OR
        (status = 'MISSING' AND value IS NULL)
    )
);
CREATE INDEX observation_series_period ON indicator_observation (series_id, period, value);
CREATE INDEX country_region ON country (region);
CREATE TABLE data_import (
    id UUID PRIMARY KEY,
    data_source_id UUID NOT NULL REFERENCES data_source(id),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    records_imported BIGINT NOT NULL DEFAULT 0 CHECK (records_imported >= 0),
    scope TEXT NOT NULL,
    error_message TEXT
);
CREATE INDEX import_source_started ON data_import (data_source_id, started_at DESC);
