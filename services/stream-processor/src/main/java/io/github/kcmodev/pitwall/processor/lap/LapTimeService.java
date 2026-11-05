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
        if (event.sessionKey() < 0)
            errors.add("sessionKey=" + event.sessionKey());
        if (event.driverNumber() < 0)
            errors.add("driverNumber=" + event.driverNumber());
        if (event.lapNumber() < 0)
            errors.add("lapNumber=" + event.lapNumber());

        if (!errors.isEmpty())
            throw new InvalidLapEventException("Negative field(s): " + String.join(", ", errors));
    }
}
