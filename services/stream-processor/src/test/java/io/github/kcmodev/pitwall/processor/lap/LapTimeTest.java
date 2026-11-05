package io.github.kcmodev.pitwall.processor.lap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LapTimeTest {

    private static final Instant START = Instant.parse("2024-03-02T15:03:12.345Z");

    @Test
    void copiesIdentityAndStartTime() {
        LapTime lap = LapTime.from(lap(91.234, 28.1, 33.2, 29.934, false));

        assertThat(lap.sessionKey()).isEqualTo(9165);
        assertThat(lap.driverNumber()).isEqualTo(44);
        assertThat(lap.lapNumber()).isEqualTo(3);
        assertThat(lap.lapStartTime()).isEqualTo(START);
        assertThat(lap.pitOutLap()).isFalse();
    }

    @ParameterizedTest(name = "{0}s -> {1}ms")
    @CsvSource({
            "91.234,  91234",
            "91.2345, 91235",   // half rounds up
            "91.2344, 91234",
            "0.0,     0",
            "28.1,    28100",   // 28.1 * 1000 is 28099.999... in floating point; rounding must not truncate it
    })
    void convertsSecondsToRoundedMilliseconds(double seconds, int expectedMs) {
        LapTime lap = LapTime.from(lap(seconds, seconds, seconds, seconds, false));

        assertThat(lap.lapDurationMs()).isEqualTo(expectedMs);
        assertThat(lap.sector1Ms()).isEqualTo(expectedMs);
        assertThat(lap.sector2Ms()).isEqualTo(expectedMs);
        assertThat(lap.sector3Ms()).isEqualTo(expectedMs);
    }

    @Test
    void missingDurationsStayNullRatherThanZero() {
        LapTime lap = LapTime.from(lap(null, null, null, null, false));

        assertThat(lap.lapDurationMs()).isNull();
        assertThat(lap.sector1Ms()).isNull();
        assertThat(lap.sector2Ms()).isNull();
        assertThat(lap.sector3Ms()).isNull();
    }

    @Test
    void lapWithDurationThatIsNotPitOutIsTimed() {
        assertThat(LapTime.from(lap(91.234, null, null, null, false)).timedLap()).isTrue();
    }

    @Test
    void pitOutLapIsNotTimedEvenWithDuration() {
        assertThat(LapTime.from(lap(105.0, null, null, null, true)).timedLap()).isFalse();
    }

    @Test
    void lapWithoutDurationIsNotTimed() {
        assertThat(LapTime.from(lap(null, 28.1, 33.2, null, false)).timedLap()).isFalse();
    }

    private static LapEvent lap(Double duration, Double s1, Double s2, Double s3, boolean pitOut) {
        return new LapEvent(9165, 44, 3, START, duration, s1, s2, s3, pitOut);
    }
}
