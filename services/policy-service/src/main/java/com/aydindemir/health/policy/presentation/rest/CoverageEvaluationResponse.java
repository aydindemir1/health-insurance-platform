package com.aydindemir.health.policy.presentation.rest;

import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;

import java.math.BigDecimal;
import java.util.UUID;

record CoverageEvaluationResponse(
        boolean eligible,
        String code,
        String reason,
        UUID policyId,
        BigDecimal remainingAmount,
        String currency) {

    static CoverageEvaluationResponse from(CoverageEvaluationResult source) {
        return new CoverageEvaluationResponse(
                source.eligible(), source.code(), source.reason(), source.policyId(),
                source.remainingAmount(), source.currency());
    }
}
