package com.aydindemir.health.claims.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.time.Instant;

@Embeddable
class PaymentJpaEmbeddable {
    @Column(name = "payment_reference", nullable = false, length = 100)
    String reference;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    BigDecimal amount;
    @Column(name = "paid_at", nullable = false)
    Instant paidAt;
}
