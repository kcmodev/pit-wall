package io.github.kcmodev.pitwall.ingest.kafka;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static io.github.kcmodev.pitwall.ingest.kafka.KafkaTopicConfig.LAPS_TOPIC;

@Component
public class StartupTestEventSender implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupTestEventSender.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    StartupTestEventSender(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String key = "44";
        kafkaTemplate.send(LAPS_TOPIC, key, "test value")
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Failed to send message to Kafka topic: {}, key: {}", LAPS_TOPIC, key, ex);
                    } else {
                        RecordMetadata recordMetadata = result.getRecordMetadata();
                        LOGGER.info("Message sent to Kafka topic: {}, key: {}, partition: {} with offset: {}", recordMetadata.topic(), key, recordMetadata.partition(), recordMetadata.offset());
                    }
                });
    }

}
