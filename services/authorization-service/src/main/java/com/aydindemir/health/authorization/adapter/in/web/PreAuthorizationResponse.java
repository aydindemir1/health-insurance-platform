package com.aydindemir.health.authorization.adapter.in.web;

import com.aydindemir.health.authorization.domain.PreAuthorization;
import com.aydindemir.health.authorization.domain.PreAuthorizationStatus;

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
        PreAuthorizationStatus status,
        String decisionReason,
        Instant createdAt,
        Instant decidedAt) {

    static PreAuthorizationResponse from(PreAuthorization source) {
        return new PreAuthorizationResponse(
                source.id(), source.memberId(), source.providerId(),
                source.policyNumber(), source.diagnosisCode(),
                source.requestedAmount(), source.currency().getCurrencyCode(),
                source.status(), source.decisionReason(), source.createdAt(),
                source.decidedAt());
    }
}
