package com.aydindemir.health.search.application.usecase;

import com.aydindemir.health.search.application.dto.SearchPage;
import com.aydindemir.health.search.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.search.application.port.in.IndexSearchRecordUseCase;
import com.aydindemir.health.search.application.port.in.SearchRecordsUseCase;
import com.aydindemir.health.search.application.port.out.SearchIndex;
import com.aydindemir.health.search.application.query.SearchRecordsQuery;
import com.aydindemir.health.search.application.security.ApplicationRole;
import com.aydindemir.health.search.domain.model.SearchRecord;
import com.aydindemir.health.search.domain.model.RecordType;

import java.util.Objects;
import java.util.UUID;

public final class SearchApplicationService implements IndexSearchRecordUseCase, SearchRecordsUseCase {
    private final SearchIndex index;
    public SearchApplicationService(SearchIndex index) { this.index = Objects.requireNonNull(index); }

    @Override public void index(SearchRecord record) { index.save(Objects.requireNonNull(record)); }

    @Override
    public SearchPage search(SearchRecordsQuery query) {
        Objects.requireNonNull(query);
        UUID providerId = providerScope(query);
        return index.search(query.text(), recordType(query.type()), query.status(),
                providerId, query.page(), query.size());
    }

    private UUID providerScope(SearchRecordsQuery query) {
        var actor = query.actor();
        if (actor.hasRole(ApplicationRole.SYSTEM_ADMIN)
                || actor.hasRole(ApplicationRole.INSURANCE_SPECIALIST)
                || actor.hasRole(ApplicationRole.CLAIM_APPROVER)) return query.providerId();
        if (actor.hasRole(ApplicationRole.HOSPITAL_USER) && actor.providerId() != null) {
            if (query.providerId() != null && !query.providerId().equals(actor.providerId())) {
                throw new ApplicationAccessDeniedException("Hospital users can only search their own provider records");
            }
            return actor.providerId();
        }
        throw new ApplicationAccessDeniedException("An operations role is required for search");
    }

    private RecordType recordType(String value) {
        if (value == null) return null;
        try { return RecordType.valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("type must be PRE_AUTHORIZATION or CLAIM");
        }
    }
}
