package io.github.kcmodev.pitwall.processor.lap;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LapTimeServiceTest {

    private final LapTimeRepository repository = mock(LapTimeRepository.class);
    private final LapTimeService service = new LapTimeService(repository);

    @Test
    void emptyJsonObjectIsRejectedAndNotStored() {
        LapEvent event = deserialize("{}");

        assertThatThrownBy(() -> service.process(event))
                .isInstanceOf(InvalidLapEventException.class)
                .hasMessageContaining("sessionKey is missing")
                .hasMessageContaining("driverNumber is missing")
                .hasMessageContaining("lapNumber is missing")
                .hasMessageContaining("pitOutLap is missing");
        verify(repository, never()).upsert(any());
    }

    @Test
    void negativeFieldIsRejected() {
        LapEvent event = new LapEvent(9165, -1, 3, null,
                null, null, null, null, false);

        assertThatThrownBy(() -> service.process(event))
                .isInstanceOf(InvalidLapEventException.class)
                .hasMessageContaining("driverNumber is negative (-1)");
        verify(repository, never()).upsert(any());
    }

    @Test
    void nullEventIsRejected() {
        assertThatThrownBy(() -> service.process(null))
                .isInstanceOf(InvalidLapEventException.class);
    }

    @Test
    void validEventIsStored() {
        LapEvent event = deserialize("""
                {"sessionKey":9165,"driverNumber":44,"lapNumber":3,"lapDurationSeconds":91.234,"pitOutLap":false}
                """);

        service.process(event);

        verify(repository).upsert(any(LapTime.class));
        assertThat(LapTime.from(event).lapDurationMs()).isEqualTo(91_234);
    }

    private static LapEvent deserialize(String json) {
        try (var deserializer = new JacksonJsonDeserializer<>(LapEvent.class, false)) {
            return deserializer.deserialize("f1.laps", json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
