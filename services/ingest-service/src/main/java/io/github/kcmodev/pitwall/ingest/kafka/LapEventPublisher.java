package io.github.kcmodev.pitwall.ingest.kafka;

import io.github.kcmodev.pitwall.ingest.lap.LapEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static io.github.kcmodev.pitwall.ingest.kafka.KafkaTopicConfig.LAPS_TOPIC;

@Component
public class LapEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(LapEventPublisher.class);
    private final KafkaTemplate<String, LapEvent> lapEventTemplate;

    LapEventPublisher(KafkaTemplate<String, LapEvent> lapEventTemplate) {
        this.lapEventTemplate = lapEventTemplate;
    }

    public CompletableFuture<SendResult<String, LapEvent>> publish(LapEvent lapEvent) {
        String key = lapEvent.sessionKey() + "-" + lapEvent.driverNumber();
        return lapEventTemplate.send(LAPS_TOPIC, key, lapEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Failed to send message to Kafka topic: {}, key: {}", LAPS_TOPIC, key, ex);
                    } else {
                        RecordMetadata recordMetadata = result.getRecordMetadata();
                        LOGGER.debug("Message sent to Kafka topic: {}, key: {}, partition: {} with offset: {}", recordMetadata.topic(), key, recordMetadata.partition(), recordMetadata.offset());
                    }
                });
    }
}
