ALTER TABLE sessions
ADD CONSTRAINT sessions_start_before_end CHECK ( date_start < date_end );