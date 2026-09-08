package com.aydindemir.health.notification.domain.model;

import com.aydindemir.health.notification.domain.valueobject.Recipient;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class NotificationDelivery {
    private final UUID taskId;
    private final UUID causationId;
    private final UUID businessReferenceId;
    private final NotificationType type;
    private final Recipient recipient;
    private final String templateKey;
    private final Instant receivedAt;
    private NotificationStatus status;
    private Instant deliveredAt;

    private NotificationDelivery(
            UUID taskId, UUID causationId, UUID businessReferenceId,
            NotificationType type, Recipient recipient, String templateKey,
            NotificationStatus status, Instant receivedAt, Instant deliveredAt) {
        this.taskId = Objects.requireNonNull(taskId);
        this.causationId = Objects.requireNonNull(causationId);
        this.businessReferenceId = Objects.requireNonNull(businessReferenceId);
        this.type = Objects.requireNonNull(type);
        this.recipient = Objects.requireNonNull(recipient);
        this.templateKey = requireText(templateKey, "templateKey");
        this.status = Objects.requireNonNull(status);
        this.receivedAt = Objects.requireNonNull(receivedAt);
        this.deliveredAt = deliveredAt;
    }

    public static NotificationDelivery receive(
            UUID taskId, UUID causationId, UUID businessReferenceId,
            NotificationType type, Recipient recipient, String templateKey, Clock clock) {
        return new NotificationDelivery(
                taskId, causationId, businessReferenceId, type, recipient,
                templateKey, NotificationStatus.RECEIVED,
                Objects.requireNonNull(clock).instant(), null);
    }

    public void markDelivered(Clock clock) {
        if (status == NotificationStatus.DELIVERED) {
            throw new IllegalStateException("Notification task is already delivered");
        }
        status = NotificationStatus.DELIVERED;
        deliveredAt = Objects.requireNonNull(clock).instant();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public UUID taskId() { return taskId; }
    public UUID causationId() { return causationId; }
    public UUID businessReferenceId() { return businessReferenceId; }
    public NotificationType type() { return type; }
    public Recipient recipient() { return recipient; }
    public String templateKey() { return templateKey; }
    public NotificationStatus status() { return status; }
    public Instant receivedAt() { return receivedAt; }
    public Instant deliveredAt() { return deliveredAt; }
}
