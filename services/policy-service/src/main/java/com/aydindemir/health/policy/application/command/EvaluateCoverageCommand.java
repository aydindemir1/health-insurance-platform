package com.aydindemir.health.policy.application.command;

import com.aydindemir.health.policy.application.security.ActorContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record EvaluateCoverageCommand(
        ActorContext actor,
        String policyNumber,
        UUID memberId,
        String serviceCode,
        BigDecimal requestedAmount,
        Currency currency,
        LocalDate serviceDate) {

    public EvaluateCoverageCommand {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(memberId);
        if (serviceCode == null || serviceCode.isBlank()) {
            throw new IllegalArgumentException("Service code must not be blank");
        }
        serviceCode = serviceCode.trim();
        Objects.requireNonNull(requestedAmount);
        Objects.requireNonNull(currency);
        if (requestedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Requested amount must be positive");
        }
        Objects.requireNonNull(serviceDate);
        if (policyNumber == null || policyNumber.isBlank()) {
            throw new IllegalArgumentException("Policy number must not be blank");
        }
        policyNumber = policyNumber.trim();
    }
}
