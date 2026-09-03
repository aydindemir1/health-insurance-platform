package com.aydindemir.health.claims.application.command;

import com.aydindemir.health.claims.application.security.ActorContext;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record CreateClaimCommand(
        ActorContext actor,
        UUID preAuthorizationId,
        String invoiceNumber,
        BigDecimal invoicedAmount,
        Currency currency) {
    public CreateClaimCommand {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(preAuthorizationId);
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("Invoice number must not be blank");
        }
        invoiceNumber = invoiceNumber.trim();
        Objects.requireNonNull(invoicedAmount);
        Objects.requireNonNull(currency);
        if (invoicedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Invoiced amount must be positive");
        }
    }
}
