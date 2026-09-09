package com.aydindemir.health.authorization.application.usecase;

import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.dto.SearchProjectionExportRecord;
import com.aydindemir.health.authorization.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.authorization.application.port.in.ExportSearchProjectionsUseCase;
import com.aydindemir.health.authorization.application.port.out.PreAuthorizationRepository;
import com.aydindemir.health.authorization.application.query.ExportSearchProjectionsQuery;
import com.aydindemir.health.authorization.application.query.PreAuthorizationSearchCriteria;
import com.aydindemir.health.authorization.application.query.SearchPreAuthorizationsQuery;
import com.aydindemir.health.authorization.application.security.ApplicationRole;
import com.aydindemir.health.authorization.domain.model.PreAuthorization;

import java.util.Objects;

public final class SearchProjectionExportService implements ExportSearchProjectionsUseCase {
    private final PreAuthorizationRepository repository;

    public SearchProjectionExportService(PreAuthorizationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public PageResult<SearchProjectionExportRecord> export(ExportSearchProjectionsQuery query) {
        Objects.requireNonNull(query);
        if (!query.actor().hasRole(ApplicationRole.SYSTEM_ADMIN)) {
            throw new ApplicationAccessDeniedException("Required role: SYSTEM_ADMIN");
        }
        var criteria = new PreAuthorizationSearchCriteria(
                null, null, null, null, query.page(), query.size(),
                SearchPreAuthorizationsQuery.SortField.CREATED_AT,
                SearchPreAuthorizationsQuery.SortDirection.ASC);
        return repository.search(criteria).map(this::toRecord);
    }

    private SearchProjectionExportRecord toRecord(PreAuthorization value) {
        return new SearchProjectionExportRecord(
                "PRE_AUTHORIZATION:" + value.id(), "PRE_AUTHORIZATION", value.id(), value.id(),
                value.memberId(), value.providerId(), value.policyNumber(), value.serviceCode(),
                value.status().name(), value.requestedAmount(),
                "APPROVED".equals(value.status().name()) ? value.requestedAmount() : null,
                value.currency().getCurrencyCode(), value.decisionReason(), value.revision() + 1,
                value.decidedAt() == null ? value.createdAt() : value.decidedAt());
    }
}
