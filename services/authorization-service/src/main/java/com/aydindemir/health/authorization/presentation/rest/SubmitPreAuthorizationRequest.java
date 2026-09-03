package com.aydindemir.health.authorization.presentation.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

record SubmitPreAuthorizationRequest(
        @NotNull UUID memberId,
        @NotBlank @Size(max = 50) String policyNumber,
        @NotBlank @Size(max = 40) String serviceCode,
        @NotBlank @Size(max = 20) String diagnosisCode,
        @NotNull @DecimalMin(value = "0.01") BigDecimal requestedAmount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
}
