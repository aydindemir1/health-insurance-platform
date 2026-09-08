package com.aydindemir.health.notification.infrastructure.persistence;

import com.aydindemir.health.notification.domain.model.NotificationStatus;
import com.aydindemir.health.notification.domain.model.NotificationType;
import com.aydindemir.health.notification.domain.valueobject.Recipient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_deliveries")
class NotificationDeliveryJpaEntity {
    @Id
    @Column(name = "task_id", nullable = false)
    UUID taskId;

    @Column(name = "causation_id", nullable = false)
    UUID causationId;

    @Column(name = "business_reference_id", nullable = false)
    UUID businessReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 60)
    NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_kind", nullable = false, length = 30)
    Recipient.RecipientKind recipientKind;

    @Column(name = "recipient_reference_id", nullable = false)
    UUID recipientReferenceId;

    @Column(name = "template_key", nullable = false, length = 120)
    String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    NotificationStatus status;

    @Column(name = "received_at", nullable = false)
    Instant receivedAt;

    @Column(name = "delivered_at")
    Instant deliveredAt;
}
