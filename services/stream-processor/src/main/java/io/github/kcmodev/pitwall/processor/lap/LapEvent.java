package io.github.kcmodev.pitwall.processor.lap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LapEvent(int sessionKey,
                       int driverNumber,
                       int lapNumber,
                       Instant lapStartTime,
                       Double lapDurationSeconds,
                       Double sector1DurationSeconds,
                       Double sector2DurationSeconds,
                       Double sector3DurationSeconds,
                       boolean pitOutLap) {}
