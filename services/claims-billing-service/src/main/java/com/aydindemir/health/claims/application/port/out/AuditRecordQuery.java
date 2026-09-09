package com.aydindemir.health.claims.application.port.out;

import com.aydindemir.health.claims.application.audit.AuditRecord;
import com.aydindemir.health.claims.application.dto.PageResult;
import com.aydindemir.health.claims.application.query.AuditRecordSearchCriteria;

public interface AuditRecordQuery {
    PageResult<AuditRecord> search(AuditRecordSearchCriteria criteria);
}
