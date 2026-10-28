package io.github.kcmodev.pitwall.ingest.replay;

import io.github.kcmodev.pitwall.ingest.kafka.LapEventPublisher;
import io.github.kcmodev.pitwall.ingest.openf1.OpenF1Client;
import io.github.kcmodev.pitwall.ingest.openf1.OpenF1Lap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ReplayService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayService.class);
    private final OpenF1Client openF1Client;
    private final LapEventPublisher lapEventPublisher;

    ReplayService(OpenF1Client openF1Client, LapEventPublisher lapEventPublisher) {
        this.openF1Client = openF1Client;
        this.lapEventPublisher = lapEventPublisher;
    }

    public ReplayResult replay(int sessionKey) {
        List<OpenF1Lap> laps = openF1Client.getLaps(sessionKey).stream()
                .sorted(Comparator.comparingInt(OpenF1Lap::lapNumber)
                        .thenComparingInt(OpenF1Lap::driverNumber))
                .toList();

        List<CompletableFuture<Boolean>> sends = laps.stream()
                .map(lap -> lapEventPublisher.publish(lap.toLapEvent())
                        .handle((result, ex) -> ex == null))
                .toList();

        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();

        long confirmed = sends.stream().filter(CompletableFuture::join).count();
        if (confirmed < laps.size()) {
            LOGGER.warn("Partial results. Replayed session {}: {} laps failed out of {}", sessionKey, laps.size() - confirmed, laps.size());
        } else {
            LOGGER.info("Replayed session {}: {}/{} laps confirmed", sessionKey, confirmed, laps.size());
        }
        return new ReplayResult(sessionKey, laps.size(), (int) confirmed, laps.size() - (int) confirmed);
    }
}
