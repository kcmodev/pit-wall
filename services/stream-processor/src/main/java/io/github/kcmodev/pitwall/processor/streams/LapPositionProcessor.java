package io.github.kcmodev.pitwall.processor.streams;

import io.github.kcmodev.pitwall.processor.lap.LapEvent;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Turns each completed lap into running positions. Input is keyed by session (after the repartition), so one
 * instance of this processor sees every driver in a session; the state store remembers who has crossed the
 * line on each lap so far.
 * <p>
 * Laps don't arrive in crossing order, so a new crossing can push drivers who were already reported down the
 * order. For every lap, the standings are computed before and after the new crossing, and a LapPosition is
 * emitted for each driver whose standing changed, including the new driver. A lap that changes nothing (the
 * same lap delivered twice, or a replay of a session already processed) emits nothing.
 * <p>
 * Emitting only changes relies on exactly-once processing: the store update, the output records and the
 * input offset commit are one transaction. With at-least-once, a crash after the store update but before
 * the output was written would reprocess the lap, find nothing changed, and never emit the lost records.
 */
class LapPositionProcessor implements Processor<String, LapEvent, String, LapPosition> {

    static final String STORE = "lap-crossings";

    private ProcessorContext<String, LapPosition> context;
    private KeyValueStore<String, LapCrossings> crossings;

    @Override
    public void init(ProcessorContext<String, LapPosition> context) {
        this.context = context;
        this.crossings = context.getStateStore(STORE);
    }

    @Override
    public void process(Record<String, LapEvent> record) {
        LapEvent lap = record.value();
        // Line crossing = when the lap started + how long it took. The topology drops laps missing either.
        long crossingMs = lap.lapStartTime().toEpochMilli() + Math.round(lap.lapDurationSeconds() * 1000);

        String storeKey = lap.sessionKey() + "-" + lap.lapNumber();
        LapCrossings before = Objects.requireNonNullElse(crossings.get(storeKey), LapCrossings.empty());
        LapCrossings after = before.with(lap.driverNumber(), crossingMs);
        if (after.equals(before)) {
            return;
        }
        crossings.put(storeKey, after);

        Map<Integer, LapCrossings.Standing> previous = before.standings();
        after.standings().forEach((driver, standing) -> {
            if (!standing.equals(previous.get(driver))) {
                LapPosition position = new LapPosition(lap.sessionKey(), driver, lap.lapNumber(),
                        standing.position(), Instant.ofEpochMilli(after.crossingMsByDriver().get(driver)),
                        standing.gapToLeaderMs(), standing.intervalToCarAheadMs());
                // Same key format as f1.laps, so each driver's records stay in order on one partition
                context.forward(record.withKey(lap.sessionKey() + "-" + driver).withValue(position));
            }
        });
    }
}
