package com.aydindemir.health.authorization.infrastructure.messaging;

import com.aydindemir.health.authorization.application.event.PreAuthorizationNotificationTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitNotificationOutboxRelayTest {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-08T12:01:00Z");

    @Test
    void marksTaskPublishedOnlyAfterPositivePublisherConfirm() {
        var fixture = fixture();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(fixture.rabbitTemplate()).send(
                eq(RabbitNotificationTopology.EXCHANGE),
                eq(RabbitNotificationTopology.ROUTING_KEY),
                any(Message.class),
                any(CorrelationData.class));

        fixture.relay().publishPending();

        assertThat(fixture.task().publishedAt).isEqualTo(PUBLISHED_AT);
        assertThat(fixture.task().publishAttempts).isEqualTo(1);
        assertThat(fixture.task().lastError).isNull();

        var messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(fixture.rabbitTemplate()).send(
                eq(RabbitNotificationTopology.EXCHANGE),
                eq(RabbitNotificationTopology.ROUTING_KEY),
                messageCaptor.capture(),
                any(CorrelationData.class));
        Message message = messageCaptor.getValue();
        assertThat(message.getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getMessageId())
                .isEqualTo(fixture.task().taskId.toString());
        assertThat(message.getMessageProperties().getCorrelationId())
                .isEqualTo(fixture.task().causationId.toString());
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"taskId\"", "\"causationId\"", "\"businessReferenceId\"",
                        "\"recipientReferenceId\"", "\"templateKey\"")
                .doesNotContain("member", "policy", "diagnosis", "amount", "reason", "token");
    }

    @Test
    void keepsTaskPendingWhenBrokerRejectsPublish() {
        var fixture = fixture();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "exchange unavailable"));
            return null;
        }).when(fixture.rabbitTemplate()).send(
                any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        fixture.relay().publishPending();

        assertThat(fixture.task().publishedAt).isNull();
        assertThat(fixture.task().publishAttempts).isEqualTo(1);
        assertThat(fixture.task().lastError).contains("exchange unavailable");
    }

    @Test
    void keepsTaskPendingWhenMandatoryPublishIsReturnedAsUnroutable() {
        var fixture = fixture();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(
                    invocation.getArgument(2), 312, "NO_ROUTE",
                    RabbitNotificationTopology.EXCHANGE,
                    RabbitNotificationTopology.ROUTING_KEY));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(fixture.rabbitTemplate()).send(
                any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        fixture.relay().publishPending();

        assertThat(fixture.task().publishedAt).isNull();
        assertThat(fixture.task().publishAttempts).isEqualTo(1);
        assertThat(fixture.task().lastError).contains("unroutable");
    }

    private Fixture fixture() {
        var repository = mock(SpringDataNotificationTaskOutboxRepository.class);
        var rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        var task = new NotificationTaskOutboxJpaEntity(new PreAuthorizationNotificationTask(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                PreAuthorizationNotificationTask.NotificationType.PRE_AUTHORIZATION_APPROVED,
                UUID.randomUUID(),
                PreAuthorizationNotificationTask.RecipientKind.PROVIDER,
                UUID.randomUUID(),
                "pre-authorization-approved",
                Instant.parse("2026-09-08T12:00:00Z")));
        when(repository.findUnpublished(any(Pageable.class))).thenReturn(List.of(task));
        var relay = new RabbitNotificationOutboxRelay(
                repository,
                rabbitTemplate,
                objectMapper,
                Clock.fixed(PUBLISHED_AT, ZoneOffset.UTC),
                50,
                Duration.ofSeconds(1));
        return new Fixture(relay, rabbitTemplate, task);
    }

    private record Fixture(
            RabbitNotificationOutboxRelay relay,
            RabbitTemplate rabbitTemplate,
            NotificationTaskOutboxJpaEntity task) {
    }
}
