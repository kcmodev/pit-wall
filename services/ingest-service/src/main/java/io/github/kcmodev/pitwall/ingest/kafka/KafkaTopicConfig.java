package io.github.kcmodev.pitwall.ingest.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic lapsTopic(@Value("${pitwall.kafka.topics.laps}") String name,
                       @Value("${pitwall.kafka.topics.laps-partitions}") int partitions,
                       @Value("${pitwall.kafka.topics.laps-replicas}") int replicas) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}