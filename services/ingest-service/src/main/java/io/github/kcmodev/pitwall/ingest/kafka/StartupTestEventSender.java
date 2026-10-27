package io.github.kcmodev.pitwall.ingest.kafka;

import io.github.kcmodev.pitwall.ingest.lap.LapEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class StartupTestEventSender implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupTestEventSender.class);
    private final LapEventPublisher lapEventPublisher;

    StartupTestEventSender(LapEventPublisher lapEventPublisher) {
        this.lapEventPublisher = lapEventPublisher;
    }

    @Override
    public void run(ApplicationArguments args) {
        LapEvent testEvent = new LapEvent(1, 10, 1, Instant.now(), 120.0,
                40.0, 40.0, 40.0, false);
        lapEventPublisher.publish(testEvent)
                .thenAccept(result -> {
                    RecordMetadata recordMetadata = result.getRecordMetadata();
                    LOGGER.info("Test message sent to Kafka topic: {}, key: {}, partition: {} with offset: {}",
                            recordMetadata.topic(), result.getProducerRecord().key(),
                            recordMetadata.partition(), recordMetadata.offset());
                });
    }

}
