package io.github.kcmodev.pitwall.processor.lap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Required fields are boxed so a missing key deserializes to null (rejected by LapTimeService)
// instead of a primitive's silent default of 0/false.
@JsonIgnoreProperties(ignoreUnknown = true)
public record LapEvent(Integer sessionKey,
                       Integer driverNumber,
                       Integer lapNumber,
                       Instant lapStartTime,
                       Double lapDurationSeconds,
                       Double sector1DurationSeconds,
                       Double sector2DurationSeconds,
                       Double sector3DurationSeconds,
                       Boolean pitOutLap) {

    /**
     * Why this event can't be processed, or an empty list if it can. Shared by the listener (which dead-letters invalid events) and the Kafka Streams topology (which drops them), so both apply the same rules.
     */
    public static List<String> validationErrors(LapEvent event) {
        if (event == null)
            return List.of("event is null");

        List<String> errors = new ArrayList<>();
        checkNonNegative(errors, "sessionKey", event.sessionKey());
        checkNonNegative(errors, "driverNumber", event.driverNumber());
        checkNonNegative(errors, "lapNumber", event.lapNumber());
        if (event.pitOutLap() == null)
            errors.add("pitOutLap is missing");
        return errors;
    }

    private static void checkNonNegative(List<String> errors, String field, Integer value) {
        if (value == null)
            errors.add(field + " is missing");
        else if (value < 0)
            errors.add(field + " is negative (" + value + ")");
    }
}
