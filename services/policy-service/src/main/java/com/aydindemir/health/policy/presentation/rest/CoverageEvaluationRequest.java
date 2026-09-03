package com.aydindemir.health.policy.presentation.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

record CoverageEvaluationRequest(
        @NotBlank @Size(max = 50) String policyNumber,
        @NotNull UUID memberId,
        @NotBlank @Size(max = 40) String serviceCode,
        @NotNull @DecimalMin("0.01") BigDecimal requestedAmount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull LocalDate serviceDate) {
}
