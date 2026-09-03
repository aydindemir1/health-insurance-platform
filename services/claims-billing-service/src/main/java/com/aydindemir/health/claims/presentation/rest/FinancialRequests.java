package com.aydindemir.health.claims.presentation.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

final class FinancialRequests {
    private FinancialRequests() {}

    record Amount(
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency) {}

    record Rejection(@NotBlank @Size(max = 500) String reason) {}

    record Payment(
            @NotBlank @Size(max = 100) String paymentReference,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency) {}
}
