package com.aydindemir.health.claims.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.messaging.search-outbox.enabled", havingValue = "true")
class KafkaClaimSearchOutboxRelay {
    static final String TOPIC = "health.claims.search-projection.v1";
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaClaimSearchOutboxRelay.class);
    private final SpringDataClaimSearchOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final int batchSize;
    private final Duration sendTimeout;

    KafkaClaimSearchOutboxRelay(
            SpringDataClaimSearchOutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock,
            @Value("${app.messaging.search-outbox.batch-size:50}") int batchSize,
            @Value("${app.messaging.search-outbox.send-timeout:5s}") Duration sendTimeout) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    @Scheduled(fixedDelayString = "${app.messaging.search-outbox.fixed-delay:1s}")
    @Transactional
    public void publishPending() {
        for (ClaimSearchOutboxJpaEntity message : repository.findUnpublished(PageRequest.of(0, batchSize))) {
            try {
                kafkaTemplate.send(TOPIC, message.claimId.toString(), message.payload)
                        .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                message.markPublished(clock.instant());
            } catch (Exception exception) {
                message.markFailed(exception);
                LOGGER.warn("Claim search outbox publish failed: eventId={}, attempt={}",
                        message.id, message.publishAttempts, exception);
            }
        }
    }
}
