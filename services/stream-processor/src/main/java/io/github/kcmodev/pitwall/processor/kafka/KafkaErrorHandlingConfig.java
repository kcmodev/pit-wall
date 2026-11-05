package io.github.kcmodev.pitwall.processor.kafka;

import io.github.kcmodev.pitwall.processor.lap.LapEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;

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
        return createErrorHandler(recoverer, retryBackOff());
    }

    // Package-private so tests can pass a mock recoverer and a zero-delay backoff
    // and check the retry/dead-letter decision without Kafka.
    static DefaultErrorHandler createErrorHandler(ConsumerRecordRecoverer recoverer, BackOff backOff) {
        var handler = new DefaultErrorHandler(recoverer, backOff);
        // Retry only failures known to be transient; everything else (bad data, bugs) goes straight to the DLT,
        // so an unexpected exception can't block a partition forever.
        handler.defaultFalse();
        handler.addRetryableExceptions(
                // Database unreachable: covers CannotGetJdbcConnectionException
                // (Spring files this under "non-transient", but a connection failure is the outage case)
                DataAccessResourceFailureException.class,
                // Deadlocks, lock timeouts, serialization failures and similar
                TransientDataAccessException.class,
                // Connection dropped mid-statement
                RecoverableDataAccessException.class);
        return handler;
    }

    // No attempt or time limit: an outage is retried until it ends rather than dead-lettering valid laps.
    // The partition blocks meanwhile (keeping lap order) and consumer lag shows the stall.
    // Keep maxInterval well under max.poll.interval.ms (5 min) so the consumer isn't evicted from the group.
    static ExponentialBackOff retryBackOff() {
        var backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(30_000);
        return backOff;
    }
}
