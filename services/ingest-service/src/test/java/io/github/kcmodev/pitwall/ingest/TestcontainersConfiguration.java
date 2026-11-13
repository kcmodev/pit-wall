package io.github.kcmodev.pitwall.ingest;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Kafka and Postgres for integration tests, started once and shared by every test class that imports this.
 * The images are the same digests as compose.yaml, so tests run against what the app runs against locally.
 * Testcontainers can't parse "name:tag@digest", so the tag lives in a comment; the digest is what pins the image.
 * {@code @ServiceConnection} points Spring Boot's Kafka and DataSource configuration at the containers,
 * overriding the addresses in application.properties and the environment.
 * <p>
 * Unlike stream-processor's copy, there's no topic bean here: ingest-service declares f1.laps itself
 * (KafkaTopicConfig), so the test also proves the app creates its own topic on startup.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        // apache/kafka:4.3.1
        return new KafkaContainer(DockerImageName.parse(
                "apache/kafka@sha256:77e3df9054047a88b520d0cc46e16696d3b22022e1d580aeccd2632df6532837"));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        // postgres:18
        return new PostgreSQLContainer(DockerImageName.parse(
                "postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6"));
    }
}
