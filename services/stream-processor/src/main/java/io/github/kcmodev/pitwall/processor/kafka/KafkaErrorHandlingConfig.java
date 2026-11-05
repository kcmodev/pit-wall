package io.github.kcmodev.pitwall.processor.kafka;

import io.github.kcmodev.pitwall.processor.lap.InvalidLapEventException;
import io.github.kcmodev.pitwall.processor.lap.LapEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;

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

    @Bean
    KafkaTemplate<String, Object> deadLetterTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        // A deserialization failure leaves the original raw bytes; any other failure leaves a LapEvent.
        // The serializer is chosen by the type of the value.
        var valueSerializer = new DelegatingByTypeSerializer(Map.of(
                byte[].class, new ByteArraySerializer(),
                LapEvent.class, new JacksonJsonSerializer<>()));
        var producerFactory = new DefaultKafkaProducerFactory<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
                new StringSerializer(), valueSerializer);
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> deadLetterTemplate,
                                     @Value("${pitwall.kafka.topics.laps-dlt}") String dlt) {
        var recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplate,
                (record, ex) -> new TopicPartition(dlt, record.partition()));

        var backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2);
        backOff.setMaxInterval(10000);

        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidLapEventException.class);
        return handler;
    }
}
