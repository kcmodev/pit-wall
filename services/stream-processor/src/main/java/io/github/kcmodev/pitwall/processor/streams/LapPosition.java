package io.github.kcmodev.pitwall.processor.streams;

import java.time.Instant;

/**
 * A driver's running position when they crossed the line to complete a lap: the JSON on f1.lap-positions.
 * <p>
 * Records are updates, not final answers: when a lap arrives that changes the order at the line (laps don't
 * arrive in crossing order), every driver whose standing changed gets a new record for the same lap.
 * A consumer upserts on (sessionKey, driverNumber, lapNumber) and keeps the latest.
 *
 * @param gapToLeaderMs        time behind the first car to complete this lap, 0 for the leader
 * @param intervalToCarAheadMs time behind the car directly ahead at the line, null for the leader
 */
public record LapPosition(int sessionKey,
                          int driverNumber,
                          int lapNumber,
                          int position,
                          Instant crossingTime,
                          long gapToLeaderMs,
                          Long intervalToCarAheadMs) {}
