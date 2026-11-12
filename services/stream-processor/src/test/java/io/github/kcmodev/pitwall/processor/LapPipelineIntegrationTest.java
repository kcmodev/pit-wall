package io.github.kcmodev.pitwall.processor;

import io.github.kcmodev.pitwall.processor.lap.InvalidLapEventException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole processor against real Kafka and Postgres: records go in through the topic, results are read from
 * the database and the dead-letter topic. Each test uses its own session key, so tests sharing one app and one
 * database never see each other's rows.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LapPipelineIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    KafkaContainer kafka;

    @Autowired
    JdbcClient jdbc;

    @Value("${pitwall.kafka.topics.laps}")
    String lapsTopic;

    @Value("${pitwall.kafka.topics.laps-dlt}")
    String deadLetterTopic;

    @Test
    void validLapIsStoredOnceWhenDeliveredTwice() {
        int session = 1001;
        String key = key(session, 44);
        String lapOne = lapJson(session, 44, 1, 91.234, false);

        send(key, lapOne);
        send(key, lapOne);   // at-least-once delivery: the same event can arrive twice
        // Same key, so same partition: processed only after both copies of lap 1
        send(key, lapJson(session, 44, 2, 90.5, false));

        await().atMost(TIMEOUT).until(() -> rows(session, 44, 2) == 1);
        assertThat(rows(session, 44, 1)).as("upsert leaves one row per lap").isEqualTo(1);

        var stored = jdbc.sql("""
                        SELECT lap_duration_ms, timed_lap, representative_lap FROM lap_times
                        WHERE session_key = :session AND driver_number = 44 AND lap_number = 1""")
                .param("session", session)
                .query((rs, n) -> List.of(rs.getInt(1), rs.getBoolean(2), rs.getBoolean(3)))
                .single();
        assertThat(stored).containsExactly(91234, true, false);   // lap 1 is timed but not representative
    }

    @Test
    void poisonPillIsDeadLetteredAndThePartitionKeepsMoving() {
        int session = 1002;
        String key = key(session, 44);

        send(key, "{this is not json");
        send(key, lapJson(session, 44, 1, 91.234, false));   // behind the poison pill in the same partition

        ConsumerRecord<String, byte[]> dead = awaitDeadLetter(key);
        assertThat(new String(dead.value(), StandardCharsets.UTF_8)).isEqualTo("{this is not json");
        assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_FQCN)).isEqualTo(DeserializationException.class.getName());
        assertThat(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(lapsTopic);

        await().atMost(TIMEOUT).until(() -> rows(session, 44, 1) == 1);
    }

    @Test
    void invalidEventIsDeadLetteredAndNotStored() {
        int session = 1003;
        String key = key(session, -1);

        send(key, """
                {"sessionKey":%d,"driverNumber":-1,"lapNumber":1,"pitOutLap":false}""".formatted(session));

        ConsumerRecord<String, byte[]> dead = awaitDeadLetter(key);
        assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)).isEqualTo(InvalidLapEventException.class.getName());
        assertThat(jdbc.sql("SELECT count(*) FROM lap_times WHERE session_key = :session")
                .param("session", session).query(Long.class).single()).isZero();
    }

    // --- helpers -------------------------------------------------------------------------------------------

    /** Publishes raw text the way ingest-service does: String key, JSON value, no type headers. */
    private void send(String key, String value) {
        try (var producer = new KafkaProducer<>(
                Map.<String, Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(lapsTopic, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish test record", e);
        }
    }

    /** Reads the dead-letter topic from the start until a record with this key shows up. */
    private ConsumerRecord<String, byte[]> awaitDeadLetter(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(deadLetterTopic));
            List<ConsumerRecord<String, byte[]>> seen = new ArrayList<>();
            await().atMost(TIMEOUT).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
                return seen.stream().anyMatch(r -> key.equals(r.key()));
            });
            return seen.stream().filter(r -> key.equals(r.key())).findFirst().orElseThrow();
        }
    }

    private long rows(int session, int driver, int lap) {
        return jdbc.sql("""
                        SELECT count(*) FROM lap_times
                        WHERE session_key = :session AND driver_number = :driver AND lap_number = :lap""")
                .param("session", session).param("driver", driver).param("lap", lap)
                .query(Long.class).single();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String key(int session, int driver) {
        return session + "-" + driver;
    }

    private static String lapJson(int session, int driver, int lap, double duration, boolean pitOut) {
        return """
                {"sessionKey":%d,"driverNumber":%d,"lapNumber":%d,"lapStartTime":"2023-09-17T12:03:44.658Z",\
                "lapDurationSeconds":%s,"pitOutLap":%s}""".formatted(session, driver, lap, duration, pitOut);
    }
}
