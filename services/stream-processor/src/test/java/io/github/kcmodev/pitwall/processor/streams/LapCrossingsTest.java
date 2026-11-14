package io.github.kcmodev.pitwall.processor.streams;

import io.github.kcmodev.pitwall.processor.streams.LapCrossings.Standing;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class LapCrossingsTest {

    @Test
    void ordersByCrossingTimeWithGapsAndIntervals() {
        LapCrossings lap = LapCrossings.empty()
                .with(44, 10_500)
                .with(1, 10_000)
                .with(16, 12_000);

        assertThat(lap.standings()).containsExactly(
                entry(1, new Standing(1, 0, null)),
                entry(44, new Standing(2, 500, 500L)),
                entry(16, new Standing(3, 2_000, 1_500L)));
    }

    @Test
    void sameCrossingTimeIsOrderedByDriverNumberNotArrival() {
        LapCrossings lap = LapCrossings.empty().with(44, 10_000).with(1, 10_000);

        assertThat(lap.standings()).containsExactly(
                entry(1, new Standing(1, 0, null)),
                entry(44, new Standing(2, 0, 0L)));
    }

    @Test
    void addingTheSameCrossingAgainChangesNothing() {
        LapCrossings lap = LapCrossings.empty().with(1, 10_000);

        assertThat(lap.with(1, 10_000)).isEqualTo(lap);
    }
}
