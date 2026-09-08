package com.aydindemir.health.claims.infrastructure.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_messages")
class ProcessedMessageJpaEntity {
    @Id @Column(name = "message_id") UUID messageId;
    @Column(name = "consumer_name", nullable = false) String consumerName;
    @Column(name = "processed_at", nullable = false) Instant processedAt;

    protected ProcessedMessageJpaEntity() {}

    ProcessedMessageJpaEntity(UUID messageId, String consumerName, Instant processedAt) {
        this.messageId = messageId;
        this.consumerName = consumerName;
        this.processedAt = processedAt;
    }
}
