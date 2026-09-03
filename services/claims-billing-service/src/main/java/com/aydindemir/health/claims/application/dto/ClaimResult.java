package com.aydindemir.health.claims.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClaimResult(
        UUID id,
        UUID preAuthorizationId,
        UUID memberId,
        UUID providerId,
        String policyNumber,
        String serviceCode,
        BigDecimal claimedAmount,
        BigDecimal approvedAmount,
        String currency,
        String status,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewStartedAt,
        Instant decidedAt) {
}
