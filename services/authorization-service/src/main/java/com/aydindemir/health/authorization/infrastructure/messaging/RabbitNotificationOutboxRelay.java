package com.aydindemir.health.authorization.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.messaging.notification-outbox.enabled", havingValue = "true")
class RabbitNotificationOutboxRelay {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitNotificationOutboxRelay.class);

    private final SpringDataNotificationTaskOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int batchSize;
    private final Duration confirmTimeout;

    RabbitNotificationOutboxRelay(
            SpringDataNotificationTaskOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.messaging.notification-outbox.batch-size:50}") int batchSize,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.messaging.notification-outbox.confirm-timeout:5s}") Duration confirmTimeout) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.batchSize = batchSize;
        this.confirmTimeout = confirmTimeout;
    }

    @Scheduled(fixedDelayString = "${app.messaging.notification-outbox.fixed-delay:1s}")
    @Transactional
    public void publishPending() {
        for (NotificationTaskOutboxJpaEntity task
                : repository.findUnpublished(PageRequest.of(0, batchSize))) {
            try {
                CorrelationData correlationData = new CorrelationData(task.taskId.toString());
                rabbitTemplate.send(
                        RabbitNotificationTopology.EXCHANGE,
                        RabbitNotificationTopology.ROUTING_KEY,
                        toMessage(task),
                        correlationData);

                CorrelationData.Confirm confirm = correlationData.getFuture()
                        .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!confirm.ack()) {
                    throw new NotificationPublishException(
                            "RabbitMQ rejected notification task: " + confirm.reason());
                }
                if (correlationData.getReturned() != null) {
                    throw new NotificationPublishException("RabbitMQ returned unroutable notification task");
                }

                task.markPublished(clock.instant());
            } catch (Exception exception) {
                task.markFailed(exception);
                LOGGER.warn(
                        "Notification task publish failed: taskId={}, notificationType={}, attempt={}",
                        task.taskId,
                        task.notificationType,
                        task.publishAttempts,
                        exception);
            }
        }
    }

    private Message toMessage(NotificationTaskOutboxJpaEntity task) {
        byte[] body = objectMapper.writeValueAsBytes(NotificationTaskMessage.from(task));
        return MessageBuilder.withBody(body)
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(task.taskId.toString())
                .setCorrelationId(task.causationId.toString())
                .setHeader("taskVersion", task.taskVersion)
                .build();
    }

    private static final class NotificationPublishException extends RuntimeException {
        private NotificationPublishException(String message) {
            super(message);
        }
    }
}
