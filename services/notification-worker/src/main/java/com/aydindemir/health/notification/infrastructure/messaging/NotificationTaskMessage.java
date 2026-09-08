package com.aydindemir.health.notification.infrastructure.messaging;

import com.aydindemir.health.notification.application.command.DeliverNotificationCommand;
import com.aydindemir.health.notification.domain.model.NotificationType;
import com.aydindemir.health.notification.domain.valueobject.Recipient;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotificationTaskMessage(
        UUID taskId,
        UUID causationId,
        int taskVersion,
        String notificationType,
        UUID businessReferenceId,
        String recipientKind,
        UUID recipientReferenceId,
        String templateKey,
        Instant occurredAt) {

    DeliverNotificationCommand toCommand() {
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(causationId, "causationId is required");
        Objects.requireNonNull(businessReferenceId, "businessReferenceId is required");
        Objects.requireNonNull(recipientReferenceId, "recipientReferenceId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (taskVersion != NotificationRabbitContract.SUPPORTED_TASK_VERSION) {
            throw new IllegalArgumentException("Unsupported notification task version: " + taskVersion);
        }
        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey is required");
        }
        return new DeliverNotificationCommand(
                taskId,
                causationId,
                businessReferenceId,
                NotificationType.valueOf(Objects.requireNonNull(
                        notificationType, "notificationType is required")),
                new Recipient(
                        Recipient.RecipientKind.valueOf(Objects.requireNonNull(
                                recipientKind, "recipientKind is required")),
                        recipientReferenceId),
                templateKey.trim());
    }
}
