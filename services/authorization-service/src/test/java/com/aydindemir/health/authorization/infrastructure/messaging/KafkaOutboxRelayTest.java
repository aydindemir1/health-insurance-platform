package com.aydindemir.health.authorization.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaOutboxRelayTest {
    @Test
    void leavesFailedMessagePendingAndPublishesItOnTheNextPoll() {
        var repository = mock(SpringDataOutboxRepository.class);
        @SuppressWarnings("unchecked")
        var kafkaTemplate = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        var message = new OutboxMessageJpaEntity(
                UUID.randomUUID(), UUID.randomUUID(), "PreAuthorizationApproved",
                1, Instant.parse("2026-09-08T12:00:00Z"), "{\"eventId\":\"test\"}");
        when(repository.findUnpublished(any(Pageable.class))).thenReturn(List.of(message));
        when(kafkaTemplate.send(
                eq(KafkaOutboxRelay.TOPIC), eq(message.aggregateId.toString()), eq(message.payload)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));
        var relay = new KafkaOutboxRelay(
                repository, kafkaTemplate,
                Clock.fixed(Instant.parse("2026-09-08T12:01:00Z"), ZoneOffset.UTC),
                50, Duration.ofSeconds(1));

        relay.publishPending();
        assertThat(message.publishedAt).isNull();
        assertThat(message.publishAttempts).isEqualTo(1);
        assertThat(message.lastError).contains("broker unavailable");

        relay.publishPending();
        assertThat(message.publishedAt).isEqualTo(Instant.parse("2026-09-08T12:01:00Z"));
        assertThat(message.publishAttempts).isEqualTo(2);
        assertThat(message.lastError).isNull();
    }
}
