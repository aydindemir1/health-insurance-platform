package com.aydindemir.health.claims.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record ClaimSearchProjectionMessage(
        UUID eventId, String eventType, int eventVersion,
        UUID claimId, UUID invoiceId, UUID preAuthorizationId,
        UUID memberId, UUID providerId, String policyNumber, String serviceCode,
        BigDecimal claimedAmount, BigDecimal approvedAmount,
        BigDecimal payableAmount, BigDecimal paidAmount,
        String currency, String claimStatus, String invoiceStatus,
        String invoiceNumber, long sourceRevision, Instant occurredAt) {
}
