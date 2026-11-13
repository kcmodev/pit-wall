package io.github.kcmodev.pitwall.processor;

import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka Streams starts inside the app against real Kafka and reports itself healthy. Same annotations as
 * LapPipelineIntegrationTest, so both share one cached application context and one set of containers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class KafkaStreamsIntegrationTest {

    @Autowired
    StreamsBuilderFactoryBean streamsFactory;

    @Autowired
    HealthIndicator kafkaStreamsHealthIndicator;

    @Test
    void streamsReachesRunningAndIsHealthy() {
        await().atMost(Duration.ofSeconds(30))
                .until(() -> streamsFactory.getKafkaStreams().state() == KafkaStreams.State.RUNNING);

        assertThat(kafkaStreamsHealthIndicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(kafkaStreamsHealthIndicator.health().getDetails()).containsEntry("state", "RUNNING");
    }
}
