package com.aydindemir.health.authorization.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PreAuthorizationNotificationTask(
        UUID taskId,
        UUID causationId,
        int taskVersion,
        NotificationType notificationType,
        UUID businessReferenceId,
        RecipientKind recipientKind,
        UUID recipientReferenceId,
        String templateKey,
        Instant occurredAt) {

    public PreAuthorizationNotificationTask {
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(causationId, "causationId is required");
        if (taskVersion < 1) {
            throw new IllegalArgumentException("taskVersion must be positive");
        }
        Objects.requireNonNull(notificationType, "notificationType is required");
        Objects.requireNonNull(businessReferenceId, "businessReferenceId is required");
        Objects.requireNonNull(recipientKind, "recipientKind is required");
        Objects.requireNonNull(recipientReferenceId, "recipientReferenceId is required");
        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey is required");
        }
        templateKey = templateKey.trim();
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    public enum NotificationType {
        PRE_AUTHORIZATION_APPROVED,
        PRE_AUTHORIZATION_REJECTED
    }

    public enum RecipientKind {
        PROVIDER
    }
}
