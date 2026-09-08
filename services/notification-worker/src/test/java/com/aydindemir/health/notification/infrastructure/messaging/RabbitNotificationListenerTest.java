package com.aydindemir.health.notification.infrastructure.messaging;

import com.aydindemir.health.notification.application.command.DeliverNotificationCommand;
import com.aydindemir.health.notification.application.port.in.DeliverNotificationUseCase;
import com.aydindemir.health.notification.domain.model.NotificationType;
import com.aydindemir.health.notification.domain.valueobject.Recipient;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RabbitNotificationListenerTest {
    @Test
    void mapsVersionedTaskAndAcknowledgesOnlyAfterSuccessfulDelivery() throws IOException {
        var useCase = mock(DeliverNotificationUseCase.class);
        var channel = mock(Channel.class);
        var task = validTask();
        var listener = new RabbitNotificationListener(useCase);

        listener.receive(task, channel, 42L);

        var command = ArgumentCaptor.forClass(DeliverNotificationCommand.class);
        verify(useCase).deliver(command.capture());
        assertThat(command.getValue().taskId()).isEqualTo(task.taskId());
        assertThat(command.getValue().type()).isEqualTo(NotificationType.PRE_AUTHORIZATION_APPROVED);
        assertThat(command.getValue().recipient()).isEqualTo(
                new Recipient(Recipient.RecipientKind.PROVIDER, task.recipientReferenceId()));
        verify(channel).basicAck(42L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void rejectsWithoutRequeueWhenApplicationDeliveryFails() throws IOException {
        var useCase = mock(DeliverNotificationUseCase.class);
        var channel = mock(Channel.class);
        doThrow(new IllegalStateException("sender unavailable"))
                .when(useCase).deliver(any(DeliverNotificationCommand.class));
        var listener = new RabbitNotificationListener(useCase);

        listener.receive(validTask(), channel, 91L);

        verify(channel).basicNack(91L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void rejectsUnsupportedContractVersionWithoutInvokingApplication() throws IOException {
        var useCase = mock(DeliverNotificationUseCase.class);
        var channel = mock(Channel.class);
        var valid = validTask();
        var unsupported = new NotificationTaskMessage(
                valid.taskId(), valid.causationId(), 2, valid.notificationType(),
                valid.businessReferenceId(), valid.recipientKind(),
                valid.recipientReferenceId(), valid.templateKey(), valid.occurredAt());
        var listener = new RabbitNotificationListener(useCase);

        listener.receive(unsupported, channel, 7L);

        verify(useCase, never()).deliver(any());
        verify(channel).basicNack(7L, false, false);
    }

    private NotificationTaskMessage validTask() {
        return new NotificationTaskMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "PRE_AUTHORIZATION_APPROVED",
                UUID.randomUUID(),
                "PROVIDER",
                UUID.randomUUID(),
                "pre-authorization-approved-v1",
                Instant.parse("2026-09-08T18:00:00Z"));
    }
}
