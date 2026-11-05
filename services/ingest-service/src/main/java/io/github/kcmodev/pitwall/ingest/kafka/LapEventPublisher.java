package io.github.kcmodev.pitwall.ingest.kafka;

import io.github.kcmodev.pitwall.ingest.lap.LapEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class LapEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(LapEventPublisher.class);
    private final KafkaTemplate<String, LapEvent> lapEventTemplate;
    private final String lapsTopic;

    LapEventPublisher(KafkaTemplate<String, LapEvent> lapEventTemplate,
                      @Value("${pitwall.kafka.topics.laps}") String lapsTopic) {
        this.lapEventTemplate = lapEventTemplate;
        this.lapsTopic = lapsTopic;
    }

    public CompletableFuture<SendResult<String, LapEvent>> publish(LapEvent lapEvent) {
        String key = lapEvent.sessionKey() + "-" + lapEvent.driverNumber();
        return lapEventTemplate.send(lapsTopic, key, lapEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Failed to send message to Kafka topic: {}, key: {}", lapsTopic, key, ex);
                    } else {
                        RecordMetadata recordMetadata = result.getRecordMetadata();
                        LOGGER.debug("Message sent to Kafka topic: {}, key: {}, partition: {} with offset: {}", recordMetadata.topic(), key, recordMetadata.partition(), recordMetadata.offset());
                    }
                });
    }
}
