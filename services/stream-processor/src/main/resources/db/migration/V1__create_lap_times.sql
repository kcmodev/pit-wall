CREATE TABLE lap_times (
    session_key         INTEGER     NOT NULL,
    driver_number       INTEGER     NOT NULL,
    lap_number          INTEGER     NOT NULL,
    lap_start_time      TIMESTAMPTZ,
    lap_duration_ms     INTEGER,    -- null when OpenF1 has no time for the lap
    sector1_ms          INTEGER,    -- null when OpenF1 has no time for this sector
    sector2_ms          INTEGER,
    sector3_ms          INTEGER,
    pit_out_lap         BOOLEAN     NOT NULL,
    timed_lap           BOOLEAN     NOT NULL,
    processed_at        TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (session_key, driver_number, lap_number)
);
