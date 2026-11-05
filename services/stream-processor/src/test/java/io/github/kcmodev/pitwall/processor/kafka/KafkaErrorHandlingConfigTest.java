package io.github.kcmodev.pitwall.processor.kafka;

import io.github.kcmodev.pitwall.processor.lap.InvalidLapEventException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KafkaErrorHandlingConfigTest {

    private final ConsumerRecordRecoverer recoverer = mock(ConsumerRecordRecoverer.class);
    private final ConsumerRecord<String, Object> record = new ConsumerRecord<>("f1.laps", 1, 42L, "9165-44", null);

    static Stream<Arguments> retried() {
        return Stream.of(
                Arguments.of("Postgres unreachable", new CannotGetJdbcConnectionException("connection refused")),
                Arguments.of("lock timeout / deadlock", new CannotAcquireLockException("lock timeout")),
                Arguments.of("connection dropped mid-statement", new RecoverableDataAccessException("connection reset")));
    }

    static Stream<Arguments> deadLettered() {
        return Stream.of(
                Arguments.of("malformed JSON", new DeserializationException("failed to deserialize", "{bad".getBytes(), false, null)),
                Arguments.of("failed validation", new InvalidLapEventException("sessionKey is missing")),
                // A non-transient DB error (e.g. a value the column rejects) will fail the same way every time
                Arguments.of("constraint violation", new DataIntegrityViolationException("value out of range")),
                Arguments.of("bug in the processor", new NullPointerException("oops")));
    }

    @ParameterizedTest(name = "{0} is retried")
    @MethodSource("retried")
    void transientFailuresAreRetriedNotDeadLettered(String description, Exception cause) {
        boolean recovered = handleOneFailure(cause);

        assertThat(recovered).as("record should be retried, not skipped").isFalse();
        verify(recoverer, never()).accept(any(), any());
    }

    @ParameterizedTest(name = "{0} is dead-lettered")
    @MethodSource("deadLettered")
    void otherFailuresAreDeadLetteredImmediately(String description, Exception cause) {
        boolean recovered = handleOneFailure(cause);

        assertThat(recovered).as("record should be sent to the DLT and skipped").isTrue();
        verify(recoverer).accept(eq(record), any());
    }

    @Test
    void retryBackOffDoublesUpToThirtySecondsAndNeverStops() {
        BackOffExecution execution = KafkaErrorHandlingConfig.retryBackOff().start();

        List<Long> intervals = new ArrayList<>();
        for (int i = 0; i < 10; i++)
            intervals.add(execution.nextBackOff());

        assertThat(intervals).containsExactly(
                1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L, 30_000L, 30_000L);
        assertThat(intervals).doesNotContain(BackOffExecution.STOP);
    }

    /**
     * Feeds one failure to the handler the way the listener container does: the listener's exception arrives
     * wrapped in ListenerExecutionFailedException, so this also checks the classifier looks through the cause chain.
     * A zero-delay backoff keeps the test fast; it only allows one retry, which is enough to see the decision.
     *
     * @return true if the handler gave up on the record (dead-lettered it), false if it will be retried
     */
    private boolean handleOneFailure(Exception cause) {
        var handler = KafkaErrorHandlingConfig.createErrorHandler(recoverer, new FixedBackOff(0, 1));
        var thrown = new ListenerExecutionFailedException("listener failed", cause);
        return handler.handleOne(thrown, record, mock(Consumer.class), mock(MessageListenerContainer.class));
    }
}
