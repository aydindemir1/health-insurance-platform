package com.aydindemir.health.authorization.infrastructure.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.messaging.outbox.enabled", matchIfMissing = true)
class KafkaOutboxRelay {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaOutboxRelay.class);
    static final String TOPIC = "health.authorization.pre-authorization.v1";

    private final SpringDataOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final int batchSize;
    private final Duration sendTimeout;

    KafkaOutboxRelay(
            SpringDataOutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.outbox.batch-size:50}") int batchSize,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.outbox.send-timeout:5s}") Duration sendTimeout) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    @Scheduled(fixedDelayString = "${app.messaging.outbox.fixed-delay:1s}")
    @Transactional
    public void publishPending() {
        for (OutboxMessageJpaEntity message : repository.findUnpublished(PageRequest.of(0, batchSize))) {
            try {
                kafkaTemplate.send(TOPIC, message.aggregateId.toString(), message.payload)
                        .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                message.markPublished(clock.instant());
            } catch (Exception exception) {
                message.markFailed(exception);
                LOGGER.warn("Outbox publish failed: eventId={}, eventType={}, attempt={}",
                        message.id, message.eventType, message.publishAttempts, exception);
            }
        }
    }
}
