package com.aydindemir.health.authorization.application.mapper;

import com.aydindemir.health.authorization.application.dto.PreAuthorizationResult;
import com.aydindemir.health.authorization.domain.model.PreAuthorization;

public final class PreAuthorizationResultMapper {
    private PreAuthorizationResultMapper() {
    }

    public static PreAuthorizationResult toResult(PreAuthorization source) {
        return new PreAuthorizationResult(
                source.id(), source.memberId(), source.providerId(),
                source.policyNumber(), source.diagnosisCode(),
                source.requestedAmount(), source.currency().getCurrencyCode(),
                source.status().name(), source.decisionReason(), source.createdAt(),
                source.decidedAt());
    }
}
