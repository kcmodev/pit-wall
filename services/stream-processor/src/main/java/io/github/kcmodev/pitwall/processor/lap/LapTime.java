package io.github.kcmodev.pitwall.processor.lap;

import java.time.Instant;

public record LapTime(int sessionKey, int driverNumber, int lapNumber, Instant lapStartTime,
                      Integer lapDurationMs, Integer sector1Ms, Integer sector2Ms, Integer sector3Ms,
                      boolean pitOutLap, boolean timedLap, boolean representativeLap) {

    public static LapTime from(LapEvent event) {
        return new LapTime(
                event.sessionKey(),
                event.driverNumber(),
                event.lapNumber(),
                event.lapStartTime(),
                convertToMs(event.lapDurationSeconds()),
                convertToMs(event.sector1DurationSeconds()),
                convertToMs(event.sector2DurationSeconds()),
                convertToMs(event.sector3DurationSeconds()),
                event.pitOutLap(),
                isTimedLap(event),
                isRepresentativeLap(event)
        );
    }

    private static Integer convertToMs(Double seconds) {
        return seconds == null ? null : (int) Math.round(seconds * 1000);
    }

    private static boolean isTimedLap(LapEvent event) {
        return event.lapDurationSeconds() != null && !event.pitOutLap();
    }

    // Race-pace laps: lap 1 starts from the grid, so it's slower than a flying lap even when it's timed
    private static boolean isRepresentativeLap(LapEvent event) {
        return isTimedLap(event) && event.lapNumber() > 1;
    }
}
