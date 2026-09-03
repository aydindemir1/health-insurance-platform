package com.aydindemir.health.authorization.application.command;

import com.aydindemir.health.authorization.application.security.ActorContext;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

public record SubmitPreAuthorizationCommand(
        ActorContext actor,
        UUID memberId,
        String policyNumber,
        String serviceCode,
        String diagnosisCode,
        BigDecimal requestedAmount,
        Currency currency) {
}
