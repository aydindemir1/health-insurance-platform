package com.aydindemir.health.policy.application.port.out;

import com.aydindemir.health.policy.application.audit.AuditRecord;
import com.aydindemir.health.policy.application.dto.PageResult;
import com.aydindemir.health.policy.application.query.AuditRecordSearchCriteria;

public interface AuditRecordQuery {
    PageResult<AuditRecord> search(AuditRecordSearchCriteria criteria);
}
