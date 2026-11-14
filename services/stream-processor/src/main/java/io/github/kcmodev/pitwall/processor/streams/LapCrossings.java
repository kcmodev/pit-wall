package io.github.kcmodev.pitwall.processor.streams;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everyone who has completed one lap of one session so far, with the time they crossed the line: the value
 * in the "lap-crossings" state store. Plain data plus pure functions, so the position logic is tested without
 * Kafka.
 * <p>
 * Order at the line is by crossing time; drivers with the same time are ordered by driver number, so the
 * result never depends on arrival order.
 */
record LapCrossings(Map<Integer, Long> crossingMsByDriver) {

    private static final Comparator<Map.Entry<Integer, Long>> CROSSING_ORDER =
            Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey());

    static LapCrossings empty() {
        return new LapCrossings(Map.of());
    }

    /** A copy with this driver's crossing added, or replaced if the driver already has one for this lap. */
    LapCrossings with(int driverNumber, long crossingMs) {
        Map<Integer, Long> updated = new HashMap<>(crossingMsByDriver);
        updated.put(driverNumber, crossingMs);
        return new LapCrossings(updated);
    }

    /** Position, gap to the leader and interval to the car ahead for every driver, in crossing order. */
    Map<Integer, Standing> standings() {
        List<Map.Entry<Integer, Long>> order = crossingMsByDriver.entrySet().stream()
                .sorted(CROSSING_ORDER)
                .toList();

        Map<Integer, Standing> standings = new LinkedHashMap<>();
        Long leaderMs = null;
        Long aheadMs = null;
        for (Map.Entry<Integer, Long> driver : order) {
            long crossingMs = driver.getValue();
            if (leaderMs == null) {
                leaderMs = crossingMs;
            }
            Long interval = aheadMs == null ? null : crossingMs - aheadMs;
            standings.put(driver.getKey(), new Standing(standings.size() + 1, crossingMs - leaderMs, interval));
            aheadMs = crossingMs;
        }
        return standings;
    }

    record Standing(int position, long gapToLeaderMs, Long intervalToCarAheadMs) {}
}
