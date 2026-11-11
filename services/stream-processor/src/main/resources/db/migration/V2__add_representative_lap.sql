-- True for laps that reflect race pace: timed (has a duration, not a pit-out lap) and not lap 1, which starts from the grid.
-- Nullable on purpose: existing rows stay NULL ("not computed yet") until the processor reprocesses them.
-- Once every row is backfilled, a later migration can add NOT NULL.
ALTER TABLE lap_times ADD COLUMN representative_lap BOOLEAN;
