package com.aydindemir.health.claims.infrastructure.persistence;

import com.aydindemir.health.claims.domain.model.ClaimStatus;
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
@Table(name = "claims")
class ClaimJpaEntity {
    @Id UUID id;
    @Column(name = "pre_authorization_id", nullable = false) UUID preAuthorizationId;
    @Column(name = "member_id", nullable = false) UUID memberId;
    @Column(name = "provider_id", nullable = false) UUID providerId;
    @Column(name = "policy_number", nullable = false, length = 50) String policyNumber;
    @Column(name = "service_code", nullable = false, length = 50) String serviceCode;
    @Column(name = "claimed_amount", nullable = false, precision = 19, scale = 2) BigDecimal claimedAmount;
    @Column(name = "approved_amount", precision = 19, scale = 2) BigDecimal approvedAmount;
    @Column(name = "currency", nullable = false, length = 3) String currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30) ClaimStatus status;
    @Column(name = "rejection_reason", length = 500) String rejectionReason;
    @Column(name = "submitted_at", nullable = false) Instant submittedAt;
    @Column(name = "review_started_at") Instant reviewStartedAt;
    @Column(name = "decided_at") Instant decidedAt;
    @Version @Column(name = "version", nullable = false) long version;
}
