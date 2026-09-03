package com.aydindemir.health.claims.presentation.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

record CreateClaimRequest(
        @NotNull UUID preAuthorizationId,
        @NotBlank @Size(max = 80) String invoiceNumber,
        @NotNull @DecimalMin(value = "0.01") BigDecimal invoicedAmount,
        @NotBlank @Size(min = 3, max = 3) String currency) {
}
