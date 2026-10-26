package io.github.kcmodev.pitwall.ingest.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    static final String LAPS_TOPIC = "f1.laps";

    @Bean
    NewTopic lapsTopic() {
        return TopicBuilder.name(LAPS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
