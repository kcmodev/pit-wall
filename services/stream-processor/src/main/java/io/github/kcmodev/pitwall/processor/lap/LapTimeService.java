package io.github.kcmodev.pitwall.processor.lap;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LapTimeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LapTimeService.class);
    private final LapTimeRepository lapTimeRepository;

    public LapTimeService(LapTimeRepository lapTimeRepository) {
        this.lapTimeRepository = lapTimeRepository;
    }

    public void process(LapEvent event) {
        verifyEvent(event);
        LapTime lap = LapTime.from(event);
        lapTimeRepository.upsert(lap);
        LOGGER.debug("Upserted lap time session={} driver={} lap={}",
                lap.sessionKey(), lap.driverNumber(), lap.lapNumber());
    }

    private static void verifyEvent(LapEvent event) {
        if (event == null)
            throw new InvalidLapEventException("Lap event is null");

        List<String> errors = new ArrayList<>();
        checkNonNegative(errors, "sessionKey", event.sessionKey());
        checkNonNegative(errors, "driverNumber", event.driverNumber());
        checkNonNegative(errors, "lapNumber", event.lapNumber());
        if (event.pitOutLap() == null)
            errors.add("pitOutLap is missing");

        if (!errors.isEmpty())
            throw new InvalidLapEventException("Invalid lap event: " + String.join(", ", errors));
    }

    private static void checkNonNegative(List<String> errors, String field, Integer value) {
        if (value == null)
            errors.add(field + " is missing");
        else if (value < 0)
            errors.add(field + " is negative (" + value + ")");
    }
}
