package com.aydindemir.health.notification.application.command;

import com.aydindemir.health.notification.domain.model.NotificationType;
import com.aydindemir.health.notification.domain.valueobject.Recipient;

import java.util.UUID;

public record DeliverNotificationCommand(
        UUID taskId,
        UUID causationId,
        UUID businessReferenceId,
        NotificationType type,
        Recipient recipient,
        String templateKey) {
}
