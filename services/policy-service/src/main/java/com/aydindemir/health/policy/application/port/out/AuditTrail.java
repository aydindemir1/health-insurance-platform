package com.aydindemir.health.policy.application.port.out;

import com.aydindemir.health.policy.application.audit.AuditRecord;

public interface AuditTrail {
    void append(AuditRecord record);
}
