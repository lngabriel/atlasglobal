-- Policy instruments are daily observations, not annual averages or year-end substitutes.
CREATE TABLE policy_rate_series (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL REFERENCES country(id),
    data_source_id UUID NOT NULL REFERENCES data_source(id),
    external_code VARCHAR(100) NOT NULL,
    frequency VARCHAR(20) NOT NULL CHECK (frequency = 'DAILY'),
    instrument TEXT NOT NULL,
    methodology TEXT NOT NULL,
    source_organization TEXT NOT NULL,
    scope_note TEXT NOT NULL,
    UNIQUE (country_id, data_source_id, external_code)
);
CREATE TABLE policy_rate_observation (
    id UUID PRIMARY KEY,
    series_id UUID NOT NULL REFERENCES policy_rate_series(id),
    observation_date DATE NOT NULL,
    value NUMERIC,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'MISSING')),
    provider_status VARCHAR(40),
    source_updated_at DATE,
    imported_at TIMESTAMPTZ NOT NULL,
    UNIQUE (series_id, observation_date),
    CHECK ((status = 'AVAILABLE' AND value IS NOT NULL) OR (status = 'MISSING' AND value IS NULL))
);
CREATE INDEX policy_rate_date ON policy_rate_observation (observation_date, series_id);
