-- World Bank's published WLD aggregates have a separate scope and never become countries.
CREATE TABLE world_indicator_observation (
    id UUID PRIMARY KEY,
    series_id UUID NOT NULL REFERENCES indicator_series(id),
    period INTEGER NOT NULL CHECK (period BETWEEN 1800 AND 2200),
    value NUMERIC,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'MISSING')),
    provider_status VARCHAR(40),
    source_updated_at DATE,
    imported_at TIMESTAMPTZ NOT NULL,
    UNIQUE (series_id, period),
    CONSTRAINT world_observation_value_status CHECK (
        (status = 'AVAILABLE' AND value IS NOT NULL) OR
        (status = 'MISSING' AND value IS NULL)
    )
);
