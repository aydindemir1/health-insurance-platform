package com.aydindemir.health.authorization.application.query;

import com.aydindemir.health.authorization.application.audit.AuditAction;

import java.util.UUID;

public record AuditRecordSearchCriteria(
        UUID aggregateId,
        AuditAction action,
        int page,
        int size) {
}
