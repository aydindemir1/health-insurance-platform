package com.aydindemir.health.notification.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.retry.support.RetryTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitListenerConfigurationTest {
    @Test
    void convertsProducerJsonWithoutJavaTypeHeaders() {
        String json = """
                {
                  "taskId":"10000000-0000-0000-0000-000000000001",
                  "causationId":"10000000-0000-0000-0000-000000000002",
                  "taskVersion":1,
                  "notificationType":"PRE_AUTHORIZATION_REJECTED",
                  "businessReferenceId":"10000000-0000-0000-0000-000000000003",
                  "recipientKind":"PROVIDER",
                  "recipientReferenceId":"10000000-0000-0000-0000-000000000004",
                  "templateKey":"pre-authorization-rejected-v1",
                  "occurredAt":"2026-09-08T18:00:00Z"
                }
                """;
        var message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        var converter = new JacksonJsonMessageConverter();
        message.getMessageProperties().setInferredArgumentType(NotificationTaskMessage.class);

        Object converted = converter.fromMessage(message);

        assertThat(converted).isInstanceOfSatisfying(NotificationTaskMessage.class, task -> {
            assertThat(task.taskVersion()).isEqualTo(1);
            assertThat(task.notificationType()).isEqualTo("PRE_AUTHORIZATION_REJECTED");
            assertThat(task.toCommand().templateKey()).isEqualTo("pre-authorization-rejected-v1");
        });
    }

    @Test
    void createsBoundedRetryPolicyFromConfigurationValues() {
        var configuration = new RabbitListenerConfiguration();

        RetryTemplate retry = configuration.notificationDeliveryRetryTemplate(
                3, java.time.Duration.ofMillis(1), 2.0, java.time.Duration.ofMillis(5));

        assertThat(retry).isNotNull();
    }
}
