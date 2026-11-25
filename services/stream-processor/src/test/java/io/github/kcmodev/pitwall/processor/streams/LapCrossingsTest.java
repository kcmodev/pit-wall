package io.github.kcmodev.pitwall.processor.streams;

import io.github.kcmodev.pitwall.processor.streams.LapCrossings.Standing;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class LapCrossingsTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    void ordersByCrossingTimeWithGapsAndIntervals() {
        LapCrossings lap = LapCrossings.empty()
                .with(44, 10_500, NOW)
                .with(1, 10_000, NOW)
                .with(16, 12_000, NOW);

        assertThat(lap.standings()).containsExactly(
                entry(1, new Standing(1, 0, null)),
                entry(44, new Standing(2, 500, 500L)),
                entry(16, new Standing(3, 2_000, 1_500L)));
    }

    @Test
    void sameCrossingTimeIsOrderedByDriverNumberNotArrival() {
        LapCrossings lap = LapCrossings.empty().with(44, 10_000, NOW).with(1, 10_000, NOW);

        assertThat(lap.standings()).containsExactly(
                entry(1, new Standing(1, 0, null)),
                entry(44, new Standing(2, 0, 0L)));
    }

    @Test
    void addingTheSameCrossingAgainChangesNothing() {
        LapCrossings lap = LapCrossings.empty().with(1, 10_000, NOW);

        assertThat(lap.with(1, 10_000, NOW + 1_000)).as("same instance, change time not refreshed").isSameAs(lap);
    }

    @Test
    void aChangeIsStampedWithTheTimeItHappened() {
        LapCrossings lap = LapCrossings.empty().with(1, 10_000, NOW).with(44, 10_500, NOW + 1_000);

        assertThat(lap.lastChangedMs()).isEqualTo(NOW + 1_000);
        assertThat(lap.unchangedSince(NOW + 1_000)).isFalse();
        assertThat(lap.unchangedSince(NOW + 1_001)).isTrue();
    }

    @Test
    void aValueStoredBeforeChangeTimesExistedReadsAsLongExpired() {
        byte[] oldFormat = "{\"crossingMsByDriver\":{\"1\":10000}}".getBytes(StandardCharsets.UTF_8);

        LapCrossings lap = RaceMetricsTopology.jsonSerde(LapCrossings.class).deserializer()
                .deserialize("lap-crossings-changelog", oldFormat);

        assertThat(lap.crossingMsByDriver()).containsEntry(1, 10_000L);
        assertThat(lap.lastChangedMs()).isZero();
    }
}
