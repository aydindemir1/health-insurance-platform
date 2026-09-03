package com.aydindemir.health.policy.presentation.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record CreatePolicyRequest(
        @NotBlank @Size(max = 50) String policyNumber,
        @NotNull UUID memberId,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validUntil,
        @NotEmpty List<@Valid CoverageRequest> coverages) {

    record CoverageRequest(
            @NotBlank @Size(max = 40) String serviceCode,
            @NotNull @DecimalMin("0.01") BigDecimal limit,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
    }
}
