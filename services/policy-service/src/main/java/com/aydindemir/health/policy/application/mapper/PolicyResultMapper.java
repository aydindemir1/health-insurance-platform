package com.aydindemir.health.policy.application.mapper;

import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;
import com.aydindemir.health.policy.application.dto.CoverageResult;
import com.aydindemir.health.policy.application.dto.PolicyResult;
import com.aydindemir.health.policy.domain.model.CoverageDecision;
import com.aydindemir.health.policy.domain.model.Policy;

public final class PolicyResultMapper {
    private PolicyResultMapper() {
    }

    public static PolicyResult toResult(Policy policy) {
        return new PolicyResult(
                policy.id(),
                policy.policyNumber(),
                policy.memberId(),
                policy.validFrom(),
                policy.validUntil(),
                policy.status().name(),
                policy.coverages().stream()
                        .map(coverage -> new CoverageResult(
                                coverage.serviceCode().value(),
                                coverage.limit().amount(),
                                coverage.used().amount(),
                                coverage.remaining().amount(),
                                coverage.limit().currency().getCurrencyCode()))
                        .toList());
    }

    public static CoverageEvaluationResult toResult(
            Policy policy,
            CoverageDecision decision) {
        return new CoverageEvaluationResult(
                decision.eligible(),
                decision.code(),
                decision.reason(),
                policy.id(),
                decision.remaining() == null ? null : decision.remaining().amount(),
                decision.remaining() == null
                        ? null
                        : decision.remaining().currency().getCurrencyCode());
    }
}
