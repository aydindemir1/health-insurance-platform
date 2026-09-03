package com.aydindemir.health.claims.application.command;

import com.aydindemir.health.claims.application.security.ActorContext;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record ResolveInvoiceDisputeCommand(
        ActorContext actor,
        UUID invoiceId,
        BigDecimal agreedPayableAmount,
        Currency currency) {
    public ResolveInvoiceDisputeCommand {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(invoiceId);
        Objects.requireNonNull(agreedPayableAmount);
        Objects.requireNonNull(currency);
    }
}
