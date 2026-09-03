package com.aydindemir.health.claims.domain.model;

import com.aydindemir.health.claims.domain.valueobject.Money;

import java.time.Instant;

public record Payment(String reference, Money amount, Instant paidAt) {
    public Payment {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Payment reference must not be blank");
        }
        reference = reference.trim();
        if (amount == null || amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("Payment date must not be null");
        }
    }
}
