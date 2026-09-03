package com.aydindemir.health.policy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
class CoverageJpaEmbeddable {
    @Column(name = "service_code", nullable = false, length = 40)
    String serviceCode;

    @Column(name = "limit_amount", nullable = false, precision = 19, scale = 2)
    BigDecimal limitAmount;

    @Column(name = "used_amount", nullable = false, precision = 19, scale = 2)
    BigDecimal usedAmount;

    @Column(name = "currency", nullable = false, length = 3)
    String currency;
}
