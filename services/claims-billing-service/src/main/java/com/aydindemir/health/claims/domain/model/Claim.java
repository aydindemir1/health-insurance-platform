package com.aydindemir.health.claims.domain.model;

import com.aydindemir.health.claims.domain.exception.InvalidClaimStateException;
import com.aydindemir.health.claims.domain.valueobject.Money;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class Claim {
    private final UUID id;
    private final UUID preAuthorizationId;
    private final UUID memberId;
    private final UUID providerId;
    private final String policyNumber;
    private final String serviceCode;
    private final Money claimedAmount;
    private final Instant submittedAt;
    private ClaimStatus status;
    private Money approvedAmount;
    private String rejectionReason;
    private Instant reviewStartedAt;
    private Instant decidedAt;

    private Claim(
            UUID id,
            UUID preAuthorizationId,
            UUID memberId,
            UUID providerId,
            String policyNumber,
            String serviceCode,
            Money claimedAmount,
            ClaimStatus status,
            Money approvedAmount,
            String rejectionReason,
            Instant submittedAt,
            Instant reviewStartedAt,
            Instant decidedAt) {
        this.id = Objects.requireNonNull(id);
        this.preAuthorizationId = Objects.requireNonNull(preAuthorizationId);
        this.memberId = Objects.requireNonNull(memberId);
        this.providerId = Objects.requireNonNull(providerId);
        this.policyNumber = requireText(policyNumber, "policyNumber");
        this.serviceCode = requireText(serviceCode, "serviceCode").toUpperCase(Locale.ROOT);
        this.claimedAmount = Objects.requireNonNull(claimedAmount);
        if (claimedAmount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Claimed amount must be positive");
        }
        this.status = Objects.requireNonNull(status);
        this.approvedAmount = approvedAmount;
        this.rejectionReason = rejectionReason;
        this.submittedAt = Objects.requireNonNull(submittedAt);
        this.reviewStartedAt = reviewStartedAt;
        this.decidedAt = decidedAt;
    }

    public static Claim submit(
            UUID id,
            UUID preAuthorizationId,
            UUID memberId,
            UUID providerId,
            String policyNumber,
            String serviceCode,
            Money claimedAmount,
            Clock clock) {
        return new Claim(id, preAuthorizationId, memberId, providerId,
                policyNumber, serviceCode, claimedAmount, ClaimStatus.SUBMITTED,
                null, null, Objects.requireNonNull(clock).instant(), null, null);
    }

    public static Claim rehydrate(
            UUID id,
            UUID preAuthorizationId,
            UUID memberId,
            UUID providerId,
            String policyNumber,
            String serviceCode,
            Money claimedAmount,
            ClaimStatus status,
            Money approvedAmount,
            String rejectionReason,
            Instant submittedAt,
            Instant reviewStartedAt,
            Instant decidedAt) {
        return new Claim(id, preAuthorizationId, memberId, providerId,
                policyNumber, serviceCode, claimedAmount, status, approvedAmount,
                rejectionReason, submittedAt, reviewStartedAt, decidedAt);
    }

    public void startReview(Clock clock) {
        requireStatus(ClaimStatus.SUBMITTED, "Only a submitted claim can enter review");
        status = ClaimStatus.UNDER_REVIEW;
        reviewStartedAt = Objects.requireNonNull(clock).instant();
    }

    public void approve(Money amount, Clock clock) {
        requireStatus(ClaimStatus.UNDER_REVIEW, "Only a claim under review can be approved");
        Objects.requireNonNull(amount);
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Approved amount must be positive");
        }
        claimedAmount.requireSameCurrency(amount);
        if (amount.isGreaterThan(claimedAmount)) {
            throw new IllegalArgumentException("Approved amount cannot exceed claimed amount");
        }
        status = ClaimStatus.APPROVED;
        approvedAmount = amount;
        decidedAt = Objects.requireNonNull(clock).instant();
    }

    public void reject(String reason, Clock clock) {
        requireStatus(ClaimStatus.UNDER_REVIEW, "Only a claim under review can be rejected");
        status = ClaimStatus.REJECTED;
        rejectionReason = requireText(reason, "rejectionReason");
        decidedAt = Objects.requireNonNull(clock).instant();
    }

    private void requireStatus(ClaimStatus expected, String message) {
        if (status != expected) {
            throw new InvalidClaimStateException(message);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public UUID preAuthorizationId() { return preAuthorizationId; }
    public UUID memberId() { return memberId; }
    public UUID providerId() { return providerId; }
    public String policyNumber() { return policyNumber; }
    public String serviceCode() { return serviceCode; }
    public Money claimedAmount() { return claimedAmount; }
    public ClaimStatus status() { return status; }
    public Money approvedAmount() { return approvedAmount; }
    public String rejectionReason() { return rejectionReason; }
    public Instant submittedAt() { return submittedAt; }
    public Instant reviewStartedAt() { return reviewStartedAt; }
    public Instant decidedAt() { return decidedAt; }
}
