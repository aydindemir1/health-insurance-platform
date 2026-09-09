package com.aydindemir.health.authorization.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PreAuthorizationDecisionEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID preAuthorizationId,
        UUID memberId,
        UUID providerId,
        String policyNumber,
        String serviceCode,
        BigDecimal requestedAmount,
        String currency,
        String decision,
        String reason,
        long sourceRevision,
        Instant occurredAt) {
}
