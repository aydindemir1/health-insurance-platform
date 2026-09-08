package com.aydindemir.health.search.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record PreAuthorizationDecisionMessage(
        UUID eventId, String eventType, int eventVersion, UUID preAuthorizationId,
        UUID memberId, UUID providerId, String policyNumber, String serviceCode,
        BigDecimal requestedAmount, String currency, String decision, String reason,
        Instant occurredAt) {
}
