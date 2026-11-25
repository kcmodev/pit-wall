package io.github.kcmodev.pitwall.processor.streams;

import io.github.kcmodev.pitwall.processor.lap.LapEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;

import java.time.Duration;
import java.util.List;

/**
 * The Kafka Streams topology that computes race metrics from f1.laps. It runs next to the {@code @KafkaListener} in the same app, as a separate consumer group (spring.kafka.streams.application-id), so both read every lap.
 * <pre>
 * f1.laps                key: session-driver
 *   filter               drop invalid laps and laps with no line-crossing time
 *   selectKey            key: session
 *   repartition          internal topic, so every lap of a session reaches the same task
 *   process              LapPositionProcessor + "lap-crossings" store (entries expire after a retention period)
 * f1.lap-positions       key: session-driver
 * </pre>
 * {@code @EnableKafkaStreams} makes Spring Boot create a StreamsBuilderFactoryBean from spring.kafka.streams.*.
 * The factory bean owns the KafkaStreams client: it builds the topology from the StreamsBuilder once every
 * {@code @Bean} below has added to it, starts the client with the application context, and closes it on shutdown.
 */
@Configuration
@EnableKafkaStreams
public class RaceMetricsTopology {

    private static final Logger LOGGER = LoggerFactory.getLogger(RaceMetricsTopology.class);

    @Bean
    KStream<String, LapPosition> lapPositions(StreamsBuilder builder,
                                              @Value("${pitwall.kafka.topics.laps}") String lapsTopic,
                                              @Value("${pitwall.kafka.topics.lap-positions}") String positionsTopic,
                                              @Value("${pitwall.streams.lap-crossings-retention}") Duration retention) {
        return build(builder, lapsTopic, positionsTopic, retention);
    }

    /** Static, so TopologyTestDriver tests build the same topology without Spring. */
    static KStream<String, LapPosition> build(StreamsBuilder builder, String lapsTopic, String positionsTopic,
                                             Duration retention) {
        // Persistent (RocksDB on local disk), with every change also written to a changelog topic in Kafka.
        // After a restart or a rebalance, the store is rebuilt from the changelog.
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(LapPositionProcessor.STORE),
                Serdes.String(), jsonSerde(LapCrossings.class)));

        // Serdes are given per operation, not as global defaults, so each step's types are visible in the code.
        // Every step is named: generated names shift when steps are added, which would rename internal topics.
        KStream<String, LapPosition> positions = builder
                .stream(lapsTopic, Consumed.with(Serdes.String(), jsonSerde(LapEvent.class)).withName("laps-source"))
                .filter(RaceMetricsTopology::hasCrossingTime, Named.as("drop-laps-without-crossing"))
                .selectKey((key, lap) -> String.valueOf(lap.sessionKey()), Named.as("key-by-session"))
                .repartition(Repartitioned.<String, LapEvent>as("laps-by-session")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(jsonSerde(LapEvent.class)))
                .process(() -> new LapPositionProcessor(retention), Named.as("compute-positions"),
                        LapPositionProcessor.STORE);

        positions.to(positionsTopic, Produced.with(Serdes.String(), jsonSerde(LapPosition.class))
                .withName("positions-sink"));
        return positions;
    }

    /**
     * Invalid events (the same rules the listener uses) are dropped here; the listener dead-letters them.
     * A valid lap without a start time or duration has no crossing time, so it can't be placed in the order.
     * In real data that's rare (1 of 1,067 laps in session 9888).
     */
    static boolean hasCrossingTime(String key, LapEvent lap) {
        List<String> errors = LapEvent.validationErrors(lap);
        if (!errors.isEmpty()) {
            LOGGER.warn("Skipping invalid lap event with key {}: {}", key, errors);
            return false;
        }
        if (lap.lapStartTime() == null || lap.lapDurationSeconds() == null) {
            LOGGER.info("Skipping lap {} for {}: no start time or duration", lap.lapNumber(), key);
            return false;
        }
        return true;
    }

    /** The output topic belongs to this topology, so the app declares it, like ingest does f1.laps. */
    @Bean
    NewTopic lapPositionsTopic(@Value("${pitwall.kafka.topics.lap-positions}") String name,
                               @Value("${pitwall.kafka.topics.lap-positions-partitions}") int partitions,
                               @Value("${pitwall.kafka.topics.lap-positions-replicas}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    /**
     * JSON for any of this topology's types. Same contract rules as the listener: the type comes from this
     * code, never from a type header, and nothing is written as a type header.
     */
    static <T> Serde<T> jsonSerde(Class<T> type) {
        return new JacksonJsonSerde<>(type).ignoreTypeHeaders().noTypeInfo();
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
