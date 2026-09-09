package com.aydindemir.health.authorization.application.usecase;

import com.aydindemir.health.authorization.application.dto.AuditRecordResult;
import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.authorization.application.port.in.SearchAuditRecordsUseCase;
import com.aydindemir.health.authorization.application.port.out.AuditRecordQuery;
import com.aydindemir.health.authorization.application.query.AuditRecordSearchCriteria;
import com.aydindemir.health.authorization.application.query.SearchAuditRecordsQuery;
import com.aydindemir.health.authorization.application.security.ApplicationRole;

import java.util.Objects;

public final class AuditQueryService implements SearchAuditRecordsUseCase {
    private final AuditRecordQuery records;

    public AuditQueryService(AuditRecordQuery records) {
        this.records = Objects.requireNonNull(records);
    }

    @Override
    public PageResult<AuditRecordResult> search(SearchAuditRecordsQuery query) {
        Objects.requireNonNull(query);
        if (!query.actor().hasRole(ApplicationRole.SYSTEM_ADMIN)) {
            throw new ApplicationAccessDeniedException("Only system administrators can read audit records");
        }
        return records.search(new AuditRecordSearchCriteria(
                        query.aggregateId(), query.action(), query.page(), query.size()))
                .map(AuditRecordResult::from);
    }
}
