package com.aydindemir.health.policy.domain.model;

import com.aydindemir.health.policy.domain.valueobject.Money;

public record CoverageDecision(
        boolean eligible,
        String code,
        String reason,
        Money remaining) {

    static CoverageDecision eligible(Money remaining) {
        return new CoverageDecision(true, "ELIGIBLE", "Coverage is available", remaining);
    }

    static CoverageDecision denied(String code, String reason, Money remaining) {
        return new CoverageDecision(false, code, reason, remaining);
    }
}
