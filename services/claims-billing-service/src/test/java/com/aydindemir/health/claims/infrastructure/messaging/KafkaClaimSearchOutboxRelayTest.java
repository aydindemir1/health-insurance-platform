package com.aydindemir.health.claims.infrastructure.messaging;

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

class KafkaClaimSearchOutboxRelayTest {
    @Test
    void keepsFailedProjectionPendingAndPublishesItOnNextPoll() {
        var repository = mock(SpringDataClaimSearchOutboxRepository.class);
        @SuppressWarnings("unchecked")
        var kafka = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        var message = new ClaimSearchOutboxJpaEntity(
                UUID.randomUUID(), UUID.randomUUID(), "ClaimSearchProjectionUpdated", 1,
                Instant.parse("2026-09-09T00:00:00Z"), "{\"claimId\":\"test\"}");
        when(repository.findUnpublished(any(Pageable.class))).thenReturn(List.of(message));
        when(kafka.send(eq(KafkaClaimSearchOutboxRelay.TOPIC),
                eq(message.claimId.toString()), eq(message.payload)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));
        var relay = new KafkaClaimSearchOutboxRelay(
                repository, kafka,
                Clock.fixed(Instant.parse("2026-09-09T00:01:00Z"), ZoneOffset.UTC),
                50, Duration.ofSeconds(1));

        relay.publishPending();
        assertThat(message.publishedAt).isNull();
        assertThat(message.publishAttempts).isEqualTo(1);

        relay.publishPending();
        assertThat(message.publishedAt).isEqualTo(Instant.parse("2026-09-09T00:01:00Z"));
        assertThat(message.publishAttempts).isEqualTo(2);
    }
}
