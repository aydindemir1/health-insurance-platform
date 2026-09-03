package com.aydindemir.health.authorization.infrastructure.persistence;

import com.aydindemir.health.authorization.domain.model.PreAuthorizationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pre_authorizations")
class PreAuthorizationJpaEntity {
    @Id
    UUID id;
    UUID memberId;
    UUID providerId;
    String policyNumber;
    String diagnosisCode;
    BigDecimal requestedAmount;
    @Column(length = 3, nullable = false)
    String currency;
    @Enumerated(EnumType.STRING)
    PreAuthorizationStatus status;
    String decisionReason;
    Instant createdAt;
    Instant decidedAt;
    @Version
    long version;

    protected PreAuthorizationJpaEntity() {
    }
}
