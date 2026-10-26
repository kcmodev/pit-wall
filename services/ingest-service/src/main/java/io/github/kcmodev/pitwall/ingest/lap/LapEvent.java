package io.github.kcmodev.pitwall.ingest.lap;

import java.time.Instant;

public record LapEvent(int sessionKey,
                       int driverNumber,
                       int lapNumber,
                       Instant lapStartTime,
                       Double lapDurationSeconds,
                       Double sector1DurationSeconds,
                       Double sector2DurationSeconds,
                       Double sector3DurationSeconds,
                       boolean pitOutLap) {}
