package io.github.kcmodev.pitwall.ingest.openf1;

import io.github.kcmodev.pitwall.ingest.lap.LapEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @JsonTest loads Spring Boot's auto-configured JsonMapper, the same one RestClient uses for OpenF1 responses,
// so these tests see the app's real Jackson settings rather than a hand-built mapper's defaults.
@JsonTest
class OpenF1LapTest {

    @Autowired
    JsonMapper jsonMapper;

    // Three real laps from session 9165 (2023 Singapore GP), saved from GET /v1/laps?session_key=9165.
    // Includes OpenF1 fields we don't map (meeting_key, speeds, segments), a lap with missing timing data,
    // and a pit-out lap.
    private List<LapEvent> sampleLaps() throws IOException {
        var resource = new ClassPathResource("openf1/laps-session-9165-sample.json");
        List<OpenF1Lap> laps = jsonMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
        return laps.stream().map(OpenF1Lap::toLapEvent).toList();
    }

    @Test
    void mapsCompleteLap() throws IOException {
        LapEvent lap = sampleLaps().getFirst();

        assertThat(lap).isEqualTo(new LapEvent(
                9165, 1, 1,
                Instant.parse("2023-09-17T12:03:44.658Z"),
                110.203, 35.639, 45.712, 28.852,
                false));
    }

    @Test
    void keepsMissingTimingDataAsNull() throws IOException {
        // Driver 24's first lap: OpenF1 has no start time, lap time or sector 1 time
        LapEvent lap = sampleLaps().get(1);

        assertThat(lap.driverNumber()).isEqualTo(24);
        assertThat(lap.lapNumber()).isEqualTo(1);
        assertThat(lap.lapStartTime()).isNull();
        assertThat(lap.lapDurationSeconds()).isNull();
        assertThat(lap.sector1DurationSeconds()).isNull();
        assertThat(lap.sector2DurationSeconds()).isEqualTo(45.252);
    }

    @Test
    void mapsPitOutLap() throws IOException {
        LapEvent lap = sampleLaps().get(2);

        assertThat(lap.lapNumber()).isEqualTo(3);
        assertThat(lap.pitOutLap()).isTrue();
        assertThat(lap.lapDurationSeconds()).isEqualTo(122.716);
    }

    @Test
    void convertsOffsetStartTimeToUtcInstant() {
        String json = """
                {"session_key":9165,"driver_number":1,"lap_number":1,"is_pit_out_lap":false,
                 "date_start":"2023-09-17T20:03:44.658+08:00"}
                """;

        LapEvent lap = jsonMapper.readValue(json, OpenF1Lap.class).toLapEvent();

        assertThat(lap.lapStartTime()).isEqualTo(Instant.parse("2023-09-17T12:03:44.658Z"));
    }

    // In real data the identity fields are never null (0 of 1091 laps in session 9165), but if OpenF1 ever sends
    // one, Jackson 3 rejects it rather than silently using 0/false. The whole response then fails to parse.
    @Test
    void nullIdentityFieldIsRejectedNotDefaultedToZero() {
        String json = """
                {"session_key":9165,"driver_number":1,"lap_number":null,"is_pit_out_lap":false}
                """;

        assertThatThrownBy(() -> jsonMapper.readValue(json, OpenF1Lap.class))
                .isInstanceOf(MismatchedInputException.class)
                .hasMessageContaining("null");
    }

    @Test
    void missingIdentityFieldIsRejectedNotDefaultedToZero() {
        String json = """
                {"session_key":9165,"driver_number":1,"is_pit_out_lap":false}
                """;

        assertThatThrownBy(() -> jsonMapper.readValue(json, OpenF1Lap.class))
                .isInstanceOf(MismatchedInputException.class);
    }
}
