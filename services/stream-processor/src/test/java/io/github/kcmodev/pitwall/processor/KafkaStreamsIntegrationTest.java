package io.github.kcmodev.pitwall.processor;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka Streams inside the app against real Kafka: it starts, reports itself healthy, and turns laps on f1.laps
 * into positions on f1.lap-positions (through the real repartition topic, state store and transactions).
 * The position logic itself is covered by RaceMetricsTopologyTest; this checks the wiring.
 * Same annotations as LapPipelineIntegrationTest, so both share one cached application context and containers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class KafkaStreamsIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    StreamsBuilderFactoryBean streamsFactory;

    @Autowired
    HealthIndicator kafkaStreamsHealthIndicator;

    @Autowired
    KafkaContainer kafka;

    @Value("${pitwall.kafka.topics.laps}")
    String lapsTopic;

    @Value("${pitwall.kafka.topics.lap-positions}")
    String positionsTopic;

    @Test
    void streamsReachesRunningAndIsHealthy() {
        awaitRunning();

        assertThat(kafkaStreamsHealthIndicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(kafkaStreamsHealthIndicator.health().getDetails()).containsEntry("state", "RUNNING");
    }

    @Test
    void lapsOnTheTopicBecomePositions() throws Exception {
        awaitRunning();
        int session = 2001;

        send(session + "-44", lapJson(session, 44, "91.000"));
        send(session + "-1", lapJson(session, 1, "90.500"));

        List<ConsumerRecord<String, String>> records = readPositions(session, 3);
        // Kafka orders records per partition, and the two keys land on different partitions, so the order
        // across drivers isn't fixed. Within one key it is: driver 44's two records arrive in emission order.
        List<String> driver44 = records.stream().filter(r -> r.key().equals(session + "-44"))
                .map(ConsumerRecord::value).toList();
        assertThat(driver44).hasSize(2);
        assertThat(records).filteredOn(r -> r.key().equals(session + "-1")).hasSize(1);

        // 44 arrived first and was reported P1; then 1 arrived, having crossed half a second earlier, so 44's
        // second record is the correction. Strict JSON: the contract on f1.lap-positions, field for field.
        JSONAssert.assertEquals("""
                {"sessionKey":2001,"driverNumber":44,"lapNumber":1,"position":2,
                 "crossingTime":"2023-09-17T12:04:31Z","gapToLeaderMs":500,"intervalToCarAheadMs":500}""",
                driver44.getLast(), JSONCompareMode.STRICT);
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private void awaitRunning() {
        await().atMost(TIMEOUT).until(() -> streamsFactory.getKafkaStreams().state() == KafkaStreams.State.RUNNING);
    }

    private void send(String key, String value) {
        try (var producer = new KafkaProducer<>(
                Map.<String, Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(lapsTopic, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish test record", e);
        }
    }

    /**
     * Reads f1.lap-positions from the start until {@code expected} records for this session have arrived.
     * read_committed: only records from committed Streams transactions, the way a downstream consumer should
     * read an exactly-once topic. The default, read_uncommitted, would also return records from aborted ones.
     */
    private List<ConsumerRecord<String, String>> readPositions(int session, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(positionsTopic));
            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            await().atMost(TIMEOUT).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (r.key().startsWith(session + "-")) {
                        seen.add(r);
                    }
                });
                return seen.size() >= expected;
            });
            return seen;
        }
    }

    private static String lapJson(int session, int driver, String durationSeconds) {
        return """
                {"sessionKey":%d,"driverNumber":%d,"lapNumber":1,"lapStartTime":"2023-09-17T12:03:00Z",\
                "lapDurationSeconds":%s,"pitOutLap":false}""".formatted(session, driver, durationSeconds);
    }
}
