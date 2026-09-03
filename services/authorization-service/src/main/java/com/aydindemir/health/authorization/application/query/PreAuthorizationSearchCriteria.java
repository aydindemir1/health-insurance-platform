package com.aydindemir.health.authorization.application.query;

import com.aydindemir.health.authorization.domain.model.PreAuthorizationStatus;

import java.util.UUID;

public record PreAuthorizationSearchCriteria(
        UUID providerId,
        PreAuthorizationStatus status,
        UUID memberId,
        String policyNumber,
        int page,
        int size,
        SearchPreAuthorizationsQuery.SortField sortBy,
        SearchPreAuthorizationsQuery.SortDirection direction) {
}
