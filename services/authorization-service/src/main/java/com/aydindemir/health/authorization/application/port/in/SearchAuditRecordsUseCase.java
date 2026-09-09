package com.aydindemir.health.authorization.application.port.in;

import com.aydindemir.health.authorization.application.dto.AuditRecordResult;
import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.query.SearchAuditRecordsQuery;

public interface SearchAuditRecordsUseCase {
    PageResult<AuditRecordResult> search(SearchAuditRecordsQuery query);
}
