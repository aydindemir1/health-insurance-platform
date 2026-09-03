package com.aydindemir.health.policy.presentation.rest;

import com.aydindemir.health.policy.application.dto.CoverageResult;
import com.aydindemir.health.policy.application.dto.PolicyResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record PolicyResponse(
        UUID id,
        String policyNumber,
        UUID memberId,
        LocalDate validFrom,
        LocalDate validUntil,
        String status,
        List<CoverageResponse> coverages) {

    static PolicyResponse from(PolicyResult source) {
        return new PolicyResponse(
                source.id(), source.policyNumber(), source.memberId(),
                source.validFrom(), source.validUntil(), source.status(),
                source.coverages().stream().map(CoverageResponse::from).toList());
    }

    record CoverageResponse(
            String serviceCode,
            BigDecimal limit,
            BigDecimal used,
            BigDecimal remaining,
            String currency) {
        static CoverageResponse from(CoverageResult source) {
            return new CoverageResponse(
                    source.serviceCode(), source.limit(), source.used(),
                    source.remaining(), source.currency());
        }
    }
}
