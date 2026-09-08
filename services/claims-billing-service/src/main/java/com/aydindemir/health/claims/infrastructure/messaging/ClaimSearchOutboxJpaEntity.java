package com.aydindemir.health.claims.infrastructure.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_search_outbox")
class ClaimSearchOutboxJpaEntity {
    @Id UUID id;
    @Column(name = "claim_id", nullable = false) UUID claimId;
    @Column(name = "event_type", nullable = false) String eventType;
    @Column(name = "event_version", nullable = false) int eventVersion;
    @Column(name = "occurred_at", nullable = false) Instant occurredAt;
    @Column(name = "payload", nullable = false) String payload;
    @Column(name = "published_at") Instant publishedAt;
    @Column(name = "publish_attempts", nullable = false) int publishAttempts;
    @Column(name = "last_error") String lastError;

    protected ClaimSearchOutboxJpaEntity() {}

    ClaimSearchOutboxJpaEntity(UUID id, UUID claimId, String eventType, int eventVersion,
                               Instant occurredAt, String payload) {
        this.id = id;
        this.claimId = claimId;
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
                ? exception.getClass().getSimpleName() : exception.getMessage();
        lastError = message.substring(0, Math.min(message.length(), 1000));
    }
}
