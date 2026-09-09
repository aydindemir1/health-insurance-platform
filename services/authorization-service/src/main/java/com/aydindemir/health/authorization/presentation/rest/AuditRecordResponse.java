package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.audit.AuditChanges;
import com.aydindemir.health.authorization.application.dto.AuditRecordResult;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

record AuditRecordResponse(
        UUID auditId,
        String aggregateType,
        UUID aggregateId,
        String action,
        String actorSubject,
        Set<String> actorRoles,
        UUID providerId,
        String correlationId,
        Instant occurredAt,
        String reasonCode,
        AuditChanges changes,
        String retentionClass) {

    static AuditRecordResponse from(AuditRecordResult source) {
        return new AuditRecordResponse(
                source.auditId(), source.aggregateType(), source.aggregateId(), source.action().name(),
                source.actorSubject(), source.actorRoles(), source.providerId(), source.correlationId(),
                source.occurredAt(), source.reasonCode().name(), source.changes(), source.retentionClass());
    }

    record Page(
            List<AuditRecordResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last) {

        static Page from(com.aydindemir.health.authorization.application.dto.PageResult<AuditRecordResult> source) {
            return new Page(
                    source.content().stream().map(AuditRecordResponse::from).toList(),
                    source.page(), source.size(), source.totalElements(), source.totalPages(),
                    source.page() == 0,
                    source.totalPages() == 0 || source.page() >= source.totalPages() - 1);
        }
    }
}
