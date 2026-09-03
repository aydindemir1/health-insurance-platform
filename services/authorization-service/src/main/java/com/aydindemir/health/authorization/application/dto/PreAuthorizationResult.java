package com.aydindemir.health.authorization.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PreAuthorizationResult(
        UUID id,
        UUID memberId,
        UUID providerId,
        String policyNumber,
        String diagnosisCode,
        BigDecimal requestedAmount,
        String currency,
        String status,
        String decisionReason,
        Instant createdAt,
        Instant decidedAt) {
}
