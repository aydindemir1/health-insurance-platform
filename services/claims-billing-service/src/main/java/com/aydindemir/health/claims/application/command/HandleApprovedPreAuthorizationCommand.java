package com.aydindemir.health.claims.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record HandleApprovedPreAuthorizationCommand(
        UUID messageId,
        UUID preAuthorizationId,
        UUID memberId,
        UUID providerId,
        String policyNumber,
        String serviceCode,
        BigDecimal authorizedAmount,
        Currency currency,
        Instant occurredAt) {
}
