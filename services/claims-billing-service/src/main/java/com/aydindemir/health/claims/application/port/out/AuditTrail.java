package com.aydindemir.health.claims.application.port.out;

import com.aydindemir.health.claims.application.audit.AuditRecord;

public interface AuditTrail {
    void append(AuditRecord record);
}
