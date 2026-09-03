package com.aydindemir.health.authorization.application;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

public record SubmitPreAuthorizationCommand(
        UUID memberId,
        UUID providerId,
        String policyNumber,
        String diagnosisCode,
        BigDecimal requestedAmount,
        Currency currency) {
}
