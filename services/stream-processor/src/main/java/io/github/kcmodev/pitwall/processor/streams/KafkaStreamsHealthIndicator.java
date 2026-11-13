package io.github.kcmodev.pitwall.processor.streams;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KafkaStreams.State;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Reports Kafka Streams' state as the "kafkaStreams" health component (the bean name minus "HealthIndicator").
 * It's in the liveness group: a Streams client in ERROR never recovers on its own, but a restart fixes it,
 * which is exactly what a liveness failure triggers. Compare the database, which is in readiness only,
 * because restarting the pod doesn't bring a database back.
 * <p>
 * Spring Boot doesn't provide a Kafka Streams health indicator, so this one is hand-written.
 */
@Component
class KafkaStreamsHealthIndicator implements HealthIndicator {

    // Terminal states. CREATED, REBALANCING and RUNNING are healthy: rebalancing is normal, not a failure.
    // NOT_RUNNING also follows a normal close, but by then the app is shutting down and nothing probes it.
    private static final Set<State> FAILED = Set.of(State.PENDING_ERROR, State.ERROR, State.NOT_RUNNING);

    private final StreamsBuilderFactoryBean streamsFactory;

    KafkaStreamsHealthIndicator(StreamsBuilderFactoryBean streamsFactory) {
        this.streamsFactory = streamsFactory;
    }

    @Override
    public Health health() {
        KafkaStreams streams = streamsFactory.getKafkaStreams();
        if (streams == null) {
            return Health.unknown().withDetail("state", "not started").build();
        }
        State state = streams.state();
        return (FAILED.contains(state) ? Health.down() : Health.up())
                .withDetail("state", state.name())
                .build();
    }
}
