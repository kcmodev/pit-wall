CREATE TABLE sessions
(
    session_key INTEGER PRIMARY KEY,
    circuit_key INTEGER NOT NULL,
    circuit_short_name TEXT NOT NULL,
    country_name TEXT NOT NULL,
    location TEXT NOT NULL,
    meeting_key INTEGER NOT NULL,
    session_name TEXT NOT NULL,
    session_type TEXT NOT NULL,
    is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    date_start TIMESTAMPTZ NOT NULL,
    date_end TIMESTAMPTZ NOT NULL
);

