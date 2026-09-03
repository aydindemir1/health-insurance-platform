package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.dto.PreAuthorizationResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record PreAuthorizationResponse(
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

    static PreAuthorizationResponse from(PreAuthorizationResult source) {
        return new PreAuthorizationResponse(
                source.id(), source.memberId(), source.providerId(),
                source.policyNumber(), source.diagnosisCode(),
                source.requestedAmount(), source.currency(),
                source.status(), source.decisionReason(), source.createdAt(),
                source.decidedAt());
    }
}
