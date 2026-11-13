package io.github.kcmodev.pitwall.processor.streams;

import io.github.kcmodev.pitwall.processor.lap.LapEvent;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;

/**
 * The Kafka Streams topology that computes race metrics from f1.laps. It runs next to the {@code @KafkaListener}
 * in the same app, as a separate consumer group (spring.kafka.streams.application-id), so both read every lap.
 * <p>
 * For now the topology only reads and logs laps; positions and gaps build on it.
 * <p>
 * {@code @EnableKafkaStreams} makes Spring Boot create a StreamsBuilderFactoryBean from spring.kafka.streams.*.
 * The factory bean owns the KafkaStreams client: it builds the topology from the StreamsBuilder once every
 * {@code @Bean} below has added to it, starts the client with the application context, and closes it on shutdown.
 */
@Configuration
@EnableKafkaStreams
public class RaceMetricsTopology {

    private static final Logger LOGGER = LoggerFactory.getLogger(RaceMetricsTopology.class);

    @Bean
    KStream<String, LapEvent> laps(StreamsBuilder builder, @Value("${pitwall.kafka.topics.laps}") String lapsTopic) {
        return build(builder, lapsTopic);
    }

    /** Static, so a TopologyTestDriver test can build the same topology without Spring. */
    static KStream<String, LapEvent> build(StreamsBuilder builder, String lapsTopic) {
        // Serdes are given per operation, not as global defaults, so each step's types are visible in the code
        KStream<String, LapEvent> laps = builder.stream(lapsTopic,
                Consumed.with(Serdes.String(), lapEventSerde()).withName("laps-source"));

        laps.peek((key, lap) -> LOGGER.debug("Lap {} for {}", lap.lapNumber(), key), Named.as("log-laps"));
        return laps;
    }

    /**
     * Reads the topic's JSON into the processor's own LapEvent. Same contract rules as the listener: the type
     * comes from this code, never from a type header, and nothing is written as a type header.
     */
    static Serde<LapEvent> lapEventSerde() {
        return new JacksonJsonSerde<>(LapEvent.class).ignoreTypeHeaders().noTypeInfo();
    }

    /**
     * What happens when a stream thread throws (a bug, not bad input: bad input is handled by the
     * deserialization handler in application.properties). SHUTDOWN_CLIENT stops Kafka Streams, its state turns
     * ERROR, and KafkaStreamsHealthIndicator fails liveness, so Kubernetes restarts the pod.
     * REPLACE_THREAD would retry the same record forever inside one pod, with nothing visible from outside.
     */
    @Bean
    StreamsBuilderFactoryBeanConfigurer uncaughtExceptionPolicy() {
        return factoryBean -> factoryBean.setStreamsUncaughtExceptionHandler(ex -> {
            LOGGER.error("Kafka Streams thread failed; shutting down Kafka Streams", ex);
            return StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });
    }
}
