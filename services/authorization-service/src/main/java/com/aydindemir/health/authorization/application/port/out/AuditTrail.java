package com.aydindemir.health.authorization.application.port.out;

import com.aydindemir.health.authorization.application.audit.AuditRecord;

public interface AuditTrail {
    void append(AuditRecord record);
}
