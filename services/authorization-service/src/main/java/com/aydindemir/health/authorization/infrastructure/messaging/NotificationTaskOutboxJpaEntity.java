package com.aydindemir.health.authorization.infrastructure.messaging;

import com.aydindemir.health.authorization.application.event.PreAuthorizationNotificationTask;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_task_outbox")
class NotificationTaskOutboxJpaEntity {
    @Id
    @Column(name = "task_id", nullable = false)
    UUID taskId;
    @Column(name = "causation_id", nullable = false)
    UUID causationId;
    @Column(name = "task_version", nullable = false)
    int taskVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 60)
    PreAuthorizationNotificationTask.NotificationType notificationType;
    @Column(name = "business_reference_id", nullable = false)
    UUID businessReferenceId;
    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_kind", nullable = false, length = 30)
    PreAuthorizationNotificationTask.RecipientKind recipientKind;
    @Column(name = "recipient_reference_id", nullable = false)
    UUID recipientReferenceId;
    @Column(name = "template_key", nullable = false, length = 120)
    String templateKey;
    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;
    @Column(name = "published_at")
    Instant publishedAt;
    @Column(name = "publish_attempts", nullable = false)
    int publishAttempts;
    @Column(name = "last_error", length = 1000)
    String lastError;

    protected NotificationTaskOutboxJpaEntity() {
    }

    NotificationTaskOutboxJpaEntity(PreAuthorizationNotificationTask task) {
        this.taskId = task.taskId();
        this.causationId = task.causationId();
        this.taskVersion = task.taskVersion();
        this.notificationType = task.notificationType();
        this.businessReferenceId = task.businessReferenceId();
        this.recipientKind = task.recipientKind();
        this.recipientReferenceId = task.recipientReferenceId();
        this.templateKey = task.templateKey();
        this.occurredAt = task.occurredAt();
    }
}
