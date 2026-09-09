package com.aydindemir.health.authorization.application.dto;

import com.aydindemir.health.authorization.application.audit.AuditAction;
import com.aydindemir.health.authorization.application.audit.AuditChanges;
import com.aydindemir.health.authorization.application.audit.AuditReasonCode;
import com.aydindemir.health.authorization.application.audit.AuditRecord;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuditRecordResult(
        UUID auditId,
        String aggregateType,
        UUID aggregateId,
        AuditAction action,
        String actorSubject,
        Set<String> actorRoles,
        UUID providerId,
        String correlationId,
        Instant occurredAt,
        AuditReasonCode reasonCode,
        AuditChanges changes,
        String retentionClass) {

    public static AuditRecordResult from(AuditRecord record) {
        return new AuditRecordResult(
                record.auditId(), record.aggregateType(), record.aggregateId(), record.action(),
                record.actorSubject(), record.actorRoles(), record.providerId(), record.correlationId(),
                record.occurredAt(), record.reasonCode(), record.changes(), record.retentionClass());
    }
}
