package com.aydindemir.health.claims.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResult(String reference, BigDecimal amount, String currency, Instant paidAt) {
}
