package com.aydindemir.health.claims.application.port.in;

import com.aydindemir.health.claims.application.dto.AuditRecordResult;
import com.aydindemir.health.claims.application.dto.PageResult;
import com.aydindemir.health.claims.application.query.SearchAuditRecordsQuery;

public interface SearchAuditRecordsUseCase {
    PageResult<AuditRecordResult> search(SearchAuditRecordsQuery query);
}
