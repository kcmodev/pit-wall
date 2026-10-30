package io.github.kcmodev.pitwall.processor.lap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class LapTimeRepository {
    private final JdbcClient jdbc;

    public LapTimeRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void upsert(LapTime lap) {
        jdbc.sql("""
                INSERT INTO lap_times (
                        session_key,
                        driver_number,
                        lap_number,
                        lap_start_time,
                        lap_duration_ms,
                        sector1_ms,
                        sector2_ms,
                        sector3_ms,
                        pit_out_lap,
                        timed_lap,
                        processed_at
                )
                VALUES (:sessionKey, :driverNumber, :lapNumber, :lapStartTime,
                        :lapDurationMs, :sector1Ms, :sector2Ms, :sector3Ms,
                        :pitOutLap, :timedLap, now())
                ON CONFLICT (session_key, driver_number, lap_number)
                DO UPDATE SET
                        lap_start_time = EXCLUDED.lap_start_time,
                        lap_duration_ms = EXCLUDED.lap_duration_ms,
                        sector1_ms = EXCLUDED.sector1_ms,
                        sector2_ms = EXCLUDED.sector2_ms,
                        sector3_ms = EXCLUDED.sector3_ms,
                        pit_out_lap = EXCLUDED.pit_out_lap,
                        timed_lap = EXCLUDED.timed_lap,
                        processed_at = now()
                """)
                .param("sessionKey", lap.sessionKey())
                .param("driverNumber", lap.driverNumber())
                .param("lapNumber", lap.lapNumber())
                .param("lapStartTime", toOffsetDateTime(lap.lapStartTime()))
                .param("lapDurationMs", lap.lapDurationMs())
                .param("sector1Ms", lap.sector1Ms())
                .param("sector2Ms", lap.sector2Ms())
                .param("sector3Ms", lap.sector3Ms())
                .param("pitOutLap", lap.pitOutLap())
                .param("timedLap", lap.timedLap())
                .update();
    }

    // pgjdbc can't bind Instant directly; OffsetDateTime maps to TIMESTAMPTZ
    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }


}
