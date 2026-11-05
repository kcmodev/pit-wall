package io.github.kcmodev.pitwall.processor.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    NewTopic lapsDeadLetterTopic(@Value("${pitwall.kafka.topics.laps-dlt}") String name,
                                 @Value("${pitwall.kafka.topics.laps-dlt-partitions}") int partitions,
                                 @Value("${pitwall.kafka.topics.laps-dlt-replicas}") int replicas) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
