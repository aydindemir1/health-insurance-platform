package com.aydindemir.health.notification.infrastructure.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "app.messaging.rabbit-listener.enabled=true",
        "app.messaging.rabbit-listener.retry.initial-interval=1ms",
        "app.messaging.rabbit-listener.retry.max-interval=2ms"
})
class RabbitNotificationDeliveryIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired RabbitAdmin rabbitAdmin;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearBrokerQueues() {
        rabbitAdmin.purgeQueue(NotificationRabbitContract.DELIVERY_QUEUE, false);
        rabbitAdmin.purgeQueue(NotificationRabbitContract.DEAD_LETTER_QUEUE, false);
        jdbc.update("delete from notification_deliveries");
    }

    @Test
    void consumesRealRabbitMessagePersistsOnceAndAcknowledgesDuplicate() {
        UUID taskId = UUID.randomUUID();
        String payload = taskJson(taskId, 1);

        publish(payload);
        publish(payload);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject(
                    "select count(*) from notification_deliveries where task_id = ? and status = 'DELIVERED'",
                    Integer.class, taskId)).isEqualTo(1);
            assertThat(messageCount(NotificationRabbitContract.DELIVERY_QUEUE)).isZero();
        });
        assertThat(messageCount(NotificationRabbitContract.DEAD_LETTER_QUEUE)).isZero();
    }

    @Test
    void rejectsUnsupportedContractVersionToRealDeadLetterQueue() {
        UUID taskId = UUID.randomUUID();

        publish(taskJson(taskId, 99));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(messageCount(NotificationRabbitContract.DEAD_LETTER_QUEUE)).isEqualTo(1));
        assertThat(jdbc.queryForObject(
                "select count(*) from notification_deliveries where task_id = ?",
                Integer.class, taskId)).isZero();
    }

    private void publish(String json) {
        var message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send(NotificationRabbitContract.EXCHANGE, NotificationRabbitContract.ROUTING_KEY, message);
    }

    private long messageCount(String queue) {
        var properties = rabbitAdmin.getQueueProperties(queue);
        assertThat(properties).isNotNull();
        return ((Number) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).longValue();
    }

    private String taskJson(UUID taskId, int taskVersion) {
        return """
                {
                  "taskId":"%s",
                  "causationId":"%s",
                  "taskVersion":%d,
                  "notificationType":"PRE_AUTHORIZATION_APPROVED",
                  "businessReferenceId":"%s",
                  "recipientKind":"PROVIDER",
                  "recipientReferenceId":"%s",
                  "templateKey":"pre-authorization-approved-v1",
                  "occurredAt":"2026-09-08T18:00:00Z"
                }
                """.formatted(taskId, UUID.randomUUID(), taskVersion, UUID.randomUUID(), UUID.randomUUID());
    }
}
