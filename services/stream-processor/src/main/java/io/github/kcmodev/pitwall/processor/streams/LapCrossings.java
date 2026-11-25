package io.github.kcmodev.pitwall.processor.streams;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everyone who has completed one lap of one session so far, with the time they crossed the line: the value
 * in the "lap-crossings" state store. Plain data plus pure functions, so the position logic is tested without
 * Kafka.
 * <p>
 * Order at the line is by crossing time; drivers with the same time are ordered by driver number, so the
 * result never depends on arrival order.
 * <p>
 * {@code lastChangedMs} is when this entry last changed, by the processing clock (not race time). The store's retention removes entries that haven't changed for a while.
 * <p>
 * Store values outlive deployments (RocksDB and the changelog), so a new field must read old values. Jackson 3 rejects a missing primitive, so the field is a boxed Long, and a missing value becomes 0: entries written before this field existed count as long expired.
 */
record LapCrossings(Map<Integer, Long> crossingMsByDriver, Long lastChangedMs) {

    private static final Comparator<Map.Entry<Integer, Long>> CROSSING_ORDER =
            Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey());

    LapCrossings {
        lastChangedMs = Objects.requireNonNullElse(lastChangedMs, 0L);
    }

    static LapCrossings empty() {
        return new LapCrossings(Map.of(), 0L);
    }

    /**
     * This driver's crossing added, or replaced if the driver already has a different one for this lap, stamped with {@code nowMs}. Returns this same instance if the driver already has exactly this crossing: nothing changed, so the change time isn't refreshed either.
     */
    LapCrossings with(int driverNumber, long crossingMs, long nowMs) {
        if (Objects.equals(crossingMsByDriver.get(driverNumber), crossingMs)) {
            return this;
        }
        Map<Integer, Long> updated = new HashMap<>(crossingMsByDriver);
        updated.put(driverNumber, crossingMs);
        return new LapCrossings(updated, nowMs);
    }

    /** True if this entry last changed before {@code cutoffMs}. */
    boolean unchangedSince(long cutoffMs) {
        return lastChangedMs < cutoffMs;
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
