package com.aydindemir.health.authorization.infrastructure.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
class OutboxMessageJpaEntity {
    @Id UUID id;
    @Column(name = "aggregate_type", nullable = false) String aggregateType;
    @Column(name = "aggregate_id", nullable = false) UUID aggregateId;
    @Column(name = "event_type", nullable = false) String eventType;
    @Column(name = "event_version", nullable = false) int eventVersion;
    @Column(name = "occurred_at", nullable = false) Instant occurredAt;
    @Column(name = "payload", nullable = false) String payload;
    @Column(name = "published_at") Instant publishedAt;
    @Column(name = "publish_attempts", nullable = false) int publishAttempts;
    @Column(name = "last_error") String lastError;

    protected OutboxMessageJpaEntity() {}

    OutboxMessageJpaEntity(
            UUID id, UUID aggregateId, String eventType, int eventVersion, Instant occurredAt, String payload) {
        this.id = id;
        this.aggregateType = "PreAuthorization";
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.occurredAt = occurredAt;
        this.payload = payload;
    }

    void markPublished(Instant now) {
        publishedAt = now;
        publishAttempts++;
        lastError = null;
    }

    void markFailed(Exception exception) {
        publishAttempts++;
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        lastError = message.substring(0, Math.min(message.length(), 1000));
    }
}
