package com.aydindemir.health.policy.application.port.in;

import com.aydindemir.health.policy.application.dto.AuditRecordResult;
import com.aydindemir.health.policy.application.dto.PageResult;
import com.aydindemir.health.policy.application.query.SearchAuditRecordsQuery;

public interface SearchAuditRecordsUseCase {
    PageResult<AuditRecordResult> search(SearchAuditRecordsQuery query);
}
