package com.aydindemir.health.claims.application.command;

import com.aydindemir.health.claims.application.security.ActorContext;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record ApproveClaimCommand(
        ActorContext actor,
        UUID claimId,
        BigDecimal approvedAmount,
        Currency currency) {
    public ApproveClaimCommand {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(approvedAmount);
        Objects.requireNonNull(currency);
    }
}
