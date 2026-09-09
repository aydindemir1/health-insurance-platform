package com.aydindemir.health.claims.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClaimSearchProjection(
        UUID claimId,
        UUID invoiceId,
        UUID preAuthorizationId,
        UUID memberId,
        UUID providerId,
        String policyNumber,
        String serviceCode,
        BigDecimal claimedAmount,
        BigDecimal approvedAmount,
        BigDecimal payableAmount,
        BigDecimal paidAmount,
        String currency,
        String claimStatus,
        String invoiceStatus,
        String invoiceNumber,
        long sourceRevision,
        Instant occurredAt) {
}
