package com.aydindemir.health.authorization.application.port.out;

import com.aydindemir.health.authorization.application.audit.AuditRecord;
import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.query.AuditRecordSearchCriteria;

public interface AuditRecordQuery {
    PageResult<AuditRecord> search(AuditRecordSearchCriteria criteria);
}
