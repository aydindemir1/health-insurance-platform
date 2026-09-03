package com.aydindemir.health.policy.application.command;

import com.aydindemir.health.policy.application.security.ActorContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreatePolicyCommand(
        ActorContext actor,
        String policyNumber,
        UUID memberId,
        LocalDate validFrom,
        LocalDate validUntil,
        List<CoverageDefinition> coverages) {

    public CreatePolicyCommand {
        Objects.requireNonNull(actor);
        if (policyNumber == null || policyNumber.isBlank()) {
            throw new IllegalArgumentException("Policy number must not be blank");
        }
        policyNumber = policyNumber.trim();
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(validFrom);
        Objects.requireNonNull(validUntil);
        coverages = List.copyOf(Objects.requireNonNull(coverages));
    }

    public record CoverageDefinition(
            String serviceCode,
            BigDecimal limit,
            Currency currency) {
        public CoverageDefinition {
            if (serviceCode == null || serviceCode.isBlank()) {
                throw new IllegalArgumentException("Service code must not be blank");
            }
            serviceCode = serviceCode.trim();
            Objects.requireNonNull(limit);
            Objects.requireNonNull(currency);
            if (limit.signum() <= 0) {
                throw new IllegalArgumentException("Coverage limit must be positive");
            }
        }
    }
}
