package io.github.kcmodev.pitwall.processor.streams;

import io.github.kcmodev.pitwall.processor.lap.LapEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The topology end to end, in-process: TopologyTestDriver runs it without a broker, including the repartition
 * and the state store, and each piped record is processed before pipeInput returns. Fast and deterministic,
 * so this is where the topology's behavior is pinned down; the integration test only checks the wiring.
 */
class RaceMetricsTopologyTest {

    private static final String LAPS = "f1.laps";
    private static final String POSITIONS = "f1.lap-positions";
    private static final Instant LAP_START = Instant.parse("2023-09-17T12:03:00Z");

    private TopologyTestDriver driver;
    private TestInputTopic<String, LapEvent> laps;
    private TestOutputTopic<String, LapPosition> positions;

    @BeforeEach
    void startTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        RaceMetricsTopology.build(builder, LAPS, POSITIONS);

        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "race-metrics-test");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");   // required, never contacted
        driver = new TopologyTestDriver(builder.build(), config);

        laps = driver.createInputTopic(LAPS, Serdes.String().serializer(),
                RaceMetricsTopology.jsonSerde(LapEvent.class).serializer());
        positions = driver.createOutputTopic(POSITIONS, Serdes.String().deserializer(),
                RaceMetricsTopology.jsonSerde(LapPosition.class).deserializer());
    }

    @AfterEach
    void closeTopology() {
        driver.close();   // also deletes the state directory
    }

    @Test
    void lapsArrivingInCrossingOrderGetPositionsGapsAndIntervals() {
        pipe(lap(1, 1, 90.0));
        pipe(lap(44, 1, 90.5));

        assertThat(positions.readKeyValuesToList()).containsExactly(
                KeyValue.pair("100-1", position(1, 1, 1, 90.0, 0, null)),
                KeyValue.pair("100-44", position(44, 1, 2, 90.5, 500, 500L)));
    }

    @Test
    void aFasterLapArrivingLateCorrectsTheDriversItPasses() {
        pipe(lap(1, 1, 90.5));    // reported as leader, but only because it arrived first
        pipe(lap(44, 1, 90.0));   // crossed earlier

        assertThat(positions.readKeyValuesToList()).containsExactly(
                KeyValue.pair("100-1", position(1, 1, 1, 90.5, 0, null)),
                KeyValue.pair("100-44", position(44, 1, 1, 90.0, 0, null)),
                KeyValue.pair("100-1", position(1, 1, 2, 90.5, 500, 500L)));   // the correction
    }

    @Test
    void driversBehindAreUnchangedWhenTheNewCrossingIsAfterThem() {
        pipe(lap(1, 1, 90.0));
        pipe(lap(44, 1, 91.0));
        positions.readKeyValuesToList();

        pipe(lap(16, 1, 92.0));

        assertThat(positions.readKeyValuesToList()).as("only the new driver is emitted").containsExactly(
                KeyValue.pair("100-16", position(16, 1, 3, 92.0, 2_000, 1_000L)));
    }

    @Test
    void theSameLapDeliveredAgainEmitsNothing() {
        pipe(lap(1, 1, 90.0));
        positions.readKeyValuesToList();

        pipe(lap(1, 1, 90.0));

        assertThat(positions.isEmpty()).isTrue();
    }

    @Test
    void lapsAreComparedOnlyWithinTheirSessionAndLap() {
        pipe(lap(1, 1, 90.0));
        pipe(lap(1, 2, 180.0));                               // the next lap: a separate order at the line
        pipe(new LapEvent(200, 44, 1, LAP_START, 85.0, null, null, null, false));   // another session

        assertThat(positions.readValuesToList()).extracting(LapPosition::position).containsExactly(1, 1, 1);
    }

    @Test
    void lapsWithoutCrossingTimeAndInvalidEventsAreSkipped() {
        pipe(new LapEvent(100, 1, 1, LAP_START, null, null, null, null, false));     // no duration
        pipe(new LapEvent(100, 1, 1, null, 90.0, null, null, null, false));          // no start time
        pipe(new LapEvent(100, -1, 1, LAP_START, 90.0, null, null, null, false));    // invalid

        assertThat(positions.isEmpty()).isTrue();
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private void pipe(LapEvent lap) {
        laps.pipeInput(lap.sessionKey() + "-" + lap.driverNumber(), lap);
    }

    /** A lap in session 100. Every lap starts at LAP_START, so the duration alone decides the crossing order. */
    private static LapEvent lap(int driver, int lapNumber, double durationSeconds) {
        return new LapEvent(100, driver, lapNumber, LAP_START, durationSeconds, null, null, null, false);
    }

    private static LapPosition position(int driver, int lapNumber, int position, double durationSeconds,
                                        long gapMs, Long intervalMs) {
        Instant crossing = LAP_START.plusMillis(Math.round(durationSeconds * 1000));
        return new LapPosition(100, driver, lapNumber, position, crossing, gapMs, intervalMs);
    }
}
