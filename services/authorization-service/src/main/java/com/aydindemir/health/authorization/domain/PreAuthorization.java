package com.aydindemir.health.authorization.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public final class PreAuthorization {
    private final UUID id;
    private final UUID memberId;
    private final UUID providerId;
    private final String policyNumber;
    private final String diagnosisCode;
    private final BigDecimal requestedAmount;
    private final Currency currency;
    private final Instant createdAt;
    private PreAuthorizationStatus status;
    private String decisionReason;
    private Instant decidedAt;

    private PreAuthorization(
            UUID id,
            UUID memberId,
            UUID providerId,
            String policyNumber,
            String diagnosisCode,
            BigDecimal requestedAmount,
            Currency currency,
            PreAuthorizationStatus status,
            String decisionReason,
            Instant createdAt,
            Instant decidedAt) {
        this.id = Objects.requireNonNull(id);
        this.memberId = Objects.requireNonNull(memberId);
        this.providerId = Objects.requireNonNull(providerId);
        this.policyNumber = requireText(policyNumber, "policyNumber");
        this.diagnosisCode = requireText(diagnosisCode, "diagnosisCode");
        if (requestedAmount == null || requestedAmount.signum() <= 0) {
            throw new IllegalArgumentException("requestedAmount must be positive");
        }
        this.requestedAmount = requestedAmount;
        this.currency = Objects.requireNonNull(currency);
        this.status = Objects.requireNonNull(status);
        this.decisionReason = decisionReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.decidedAt = decidedAt;
    }

    public static PreAuthorization submit(
            UUID memberId,
            UUID providerId,
            String policyNumber,
            String diagnosisCode,
            BigDecimal requestedAmount,
            Currency currency,
            Clock clock) {
        return new PreAuthorization(
                UUID.randomUUID(), memberId, providerId, policyNumber, diagnosisCode,
                requestedAmount, currency, PreAuthorizationStatus.PENDING, null,
                clock.instant(), null);
    }

    public static PreAuthorization rehydrate(
            UUID id,
            UUID memberId,
            UUID providerId,
            String policyNumber,
            String diagnosisCode,
            BigDecimal requestedAmount,
            Currency currency,
            PreAuthorizationStatus status,
            String decisionReason,
            Instant createdAt,
            Instant decidedAt) {
        return new PreAuthorization(
                id, memberId, providerId, policyNumber, diagnosisCode,
                requestedAmount, currency, status, decisionReason, createdAt, decidedAt);
    }

    public void approve(String reason, Clock clock) {
        decide(PreAuthorizationStatus.APPROVED, reason, clock);
    }

    public void reject(String reason, Clock clock) {
        decide(PreAuthorizationStatus.REJECTED, requireText(reason, "reason"), clock);
    }

    private void decide(PreAuthorizationStatus targetStatus, String reason, Clock clock) {
        if (status != PreAuthorizationStatus.PENDING) {
            throw new InvalidPreAuthorizationStateException(
                    "Only a pending pre-authorization can be decided");
        }
        status = targetStatus;
        decisionReason = normalize(reason);
        decidedAt = clock.instant();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public UUID memberId() { return memberId; }
    public UUID providerId() { return providerId; }
    public String policyNumber() { return policyNumber; }
    public String diagnosisCode() { return diagnosisCode; }
    public BigDecimal requestedAmount() { return requestedAmount; }
    public Currency currency() { return currency; }
    public PreAuthorizationStatus status() { return status; }
    public String decisionReason() { return decisionReason; }
    public Instant createdAt() { return createdAt; }
    public Instant decidedAt() { return decidedAt; }
}
