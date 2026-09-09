package com.aydindemir.health.policy.infrastructure.configuration;

import com.aydindemir.health.policy.application.dto.AuditRecordResult;
import com.aydindemir.health.policy.application.dto.PageResult;
import com.aydindemir.health.policy.application.port.in.SearchAuditRecordsUseCase;
import com.aydindemir.health.policy.application.query.SearchAuditRecordsQuery;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalAuditQuery implements SearchAuditRecordsUseCase {
    private final SearchAuditRecordsUseCase delegate;

    public TransactionalAuditQuery(SearchAuditRecordsUseCase delegate) { this.delegate = delegate; }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditRecordResult> search(SearchAuditRecordsQuery query) {
        return delegate.search(query);
    }
}
