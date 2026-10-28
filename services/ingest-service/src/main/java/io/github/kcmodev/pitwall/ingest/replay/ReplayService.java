package io.github.kcmodev.pitwall.ingest.replay;

import io.github.kcmodev.pitwall.ingest.kafka.LapEventPublisher;
import io.github.kcmodev.pitwall.ingest.openf1.OpenF1Client;
import io.github.kcmodev.pitwall.ingest.openf1.OpenF1Lap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class ReplayService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayService.class);
    private final OpenF1Client openF1Client;
    private final LapEventPublisher lapEventPublisher;

    ReplayService(OpenF1Client openF1Client, LapEventPublisher lapEventPublisher) {
        this.openF1Client = openF1Client;
        this.lapEventPublisher = lapEventPublisher;
    }

    public int replay(int sessionKey) {
        List<OpenF1Lap> laps = openF1Client.getLaps(sessionKey).stream()
                .sorted(Comparator.comparingInt(OpenF1Lap::lapNumber)
                        .thenComparingInt(OpenF1Lap::driverNumber))
                .toList();
        laps.forEach(lap -> lapEventPublisher.publish(lap.toLapEvent()));
        LOGGER.info("Replaying session {}: {} laps queued for publishing", sessionKey, laps.size());
        return laps.size();
    }
}
