package com.aydindemir.health.claims.application.command;

import com.aydindemir.health.claims.application.security.ActorContext;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record RecordPaymentCommand(
        ActorContext actor,
        UUID invoiceId,
        String paymentReference,
        BigDecimal amount,
        Currency currency) {
    public RecordPaymentCommand {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(invoiceId);
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("Payment reference must not be blank");
        }
        paymentReference = paymentReference.trim();
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
    }
}
