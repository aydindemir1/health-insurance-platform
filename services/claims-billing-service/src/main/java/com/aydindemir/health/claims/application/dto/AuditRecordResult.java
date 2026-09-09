package com.aydindemir.health.claims.application.dto;

import com.aydindemir.health.claims.application.audit.AuditRecord;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuditRecordResult(
        UUID auditId, String aggregateType, UUID aggregateId, String action,
        String actorSubject, Set<String> actorRoles, UUID providerId,
        String correlationId, Instant occurredAt, String reasonCode,
        String fromStatus, String toStatus, String retentionClass) {
    public static AuditRecordResult from(AuditRecord source) {
        return new AuditRecordResult(
                source.auditId(), source.aggregateType(), source.aggregateId(), source.action(),
                source.actorSubject(), source.actorRoles(), source.providerId(), source.correlationId(),
                source.occurredAt(), source.reasonCode(), source.fromStatus(), source.toStatus(),
                source.retentionClass());
    }
}
