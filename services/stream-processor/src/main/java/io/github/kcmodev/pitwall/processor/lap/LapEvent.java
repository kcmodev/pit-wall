package io.github.kcmodev.pitwall.processor.lap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

// Required fields are boxed so a missing key deserializes to null (rejected by LapTimeService)
// instead of a primitive's silent default of 0/false.
@JsonIgnoreProperties(ignoreUnknown = true)
public record LapEvent(Integer sessionKey,
                       Integer driverNumber,
                       Integer lapNumber,
                       Instant lapStartTime,
                       Double lapDurationSeconds,
                       Double sector1DurationSeconds,
                       Double sector2DurationSeconds,
                       Double sector3DurationSeconds,
                       Boolean pitOutLap) {}
