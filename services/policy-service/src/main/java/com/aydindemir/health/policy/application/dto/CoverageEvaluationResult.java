package com.aydindemir.health.policy.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CoverageEvaluationResult(
        boolean eligible,
        String code,
        String reason,
        UUID policyId,
        BigDecimal remainingAmount,
        String currency) {

    public static CoverageEvaluationResult policyNotFound() {
        return new CoverageEvaluationResult(
                false, "POLICY_NOT_FOUND", "Policy was not found", null, null, null);
    }
}
