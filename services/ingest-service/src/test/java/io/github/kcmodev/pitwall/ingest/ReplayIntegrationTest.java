package io.github.kcmodev.pitwall.ingest;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The whole ingest path against real Kafka and Postgres: an HTTP request goes in through the replay endpoint,
 * and the result is read back from f1.laps the way a consumer would see it. OpenF1 is the one fake:
 * MockRestServiceServer answers the RestClient's calls with saved OpenF1 data, so the test is repeatable and
 * doesn't depend on a public API being up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
@Import(TestcontainersConfiguration.class)
class ReplayIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MockRestServiceServer openF1;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    JdbcClient jdbc;

    @Value("${pitwall.kafka.topics.laps}")
    String lapsTopic;

    @Test
    void startsReadyWithMigrationsApplied() {
        assertThat(jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success")
                .query(Long.class).single()).as("applied migrations").isEqualTo(2);

        // The readiness group includes the database, so UP means Postgres is reachable
        assertThat(mvc.get().uri("/actuator/health/readiness"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void replayPublishesEachLapKeyedBySessionAndDriver() throws Exception {
        openF1.expect(requestTo(endsWith("/laps?session_key=9165")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new ClassPathResource("openf1/laps-session-9165-sample.json"),
                        MediaType.APPLICATION_JSON));

        assertThat(mvc.post().uri("/api/replays/9165"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"sessionKey":9165,"totalLaps":3,"lapsPublished":3,"lapsFailed":0}""");
        openF1.verify();

        List<ConsumerRecord<String, String>> laps = readLaps("9165-", 3);
        assertThat(laps).extracting(ConsumerRecord::key)
                .containsExactlyInAnyOrder("9165-1", "9165-24", "9165-24");
        // Type headers are disabled: consumers read the JSON, not a Java class name from ingest-service
        assertThat(laps).allSatisfy(r -> assertThat(r.headers().lastHeader("__TypeId__")).isNull());

        // The JSON on the topic is the contract with stream-processor. NON_EXTENSIBLE fails on a renamed,
        // missing or extra field, but ignores field order.
        String completeLap = laps.stream().filter(r -> r.key().equals("9165-1")).findFirst().orElseThrow().value();
        JSONAssert.assertEquals("""
                {"sessionKey":9165,"driverNumber":1,"lapNumber":1,"lapStartTime":"2023-09-17T12:03:44.658Z",
                 "lapDurationSeconds":110.203,"sector1DurationSeconds":35.639,"sector2DurationSeconds":45.712,
                 "sector3DurationSeconds":28.852,"pitOutLap":false}""",
                completeLap, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void unknownSessionReturnsNotFound() {
        // OpenF1 answers 404 when a query matches nothing; OpenF1Client turns that into an empty list
        openF1.expect(requestTo(endsWith("/laps?session_key=1")))
                .andRespond(withResourceNotFound());

        assertThat(mvc.post().uri("/api/replays/1")).hasStatus(HttpStatus.NOT_FOUND);
        openF1.verify();
    }

    // --- helpers -------------------------------------------------------------------------------------------

    /**
     * Reads f1.laps from the start with a throwaway consumer group until {@code expected} records whose key
     * starts with {@code keyPrefix} have arrived. Values are read as plain strings, so the test sees the
     * exact JSON a consumer receives.
     */
    private List<ConsumerRecord<String, String>> readLaps(String keyPrefix, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(lapsTopic));
            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            await().atMost(TIMEOUT).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (r.key().startsWith(keyPrefix)) {
                        seen.add(r);
                    }
                });
                return seen.size() >= expected;
            });
            return seen;
        }
    }
}
