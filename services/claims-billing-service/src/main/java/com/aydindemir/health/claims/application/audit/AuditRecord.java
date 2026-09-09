package com.aydindemir.health.claims.application.audit;

import com.aydindemir.health.claims.application.security.ActorContext;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuditRecord(
        UUID auditId, String aggregateType, UUID aggregateId, String action,
        String actorSubject, Set<String> actorRoles, UUID providerId,
        String correlationId, Instant occurredAt, String reasonCode,
        String fromStatus, String toStatus, String retentionClass) {

    public AuditRecord {
        Objects.requireNonNull(auditId);
        Objects.requireNonNull(aggregateId);
        aggregateType = controlled(aggregateType, "aggregateType");
        action = controlled(action, "action");
        actorSubject = text(actorSubject, "actorSubject");
        actorRoles = Set.copyOf(Objects.requireNonNull(actorRoles));
        if (actorRoles.isEmpty()) throw new IllegalArgumentException("actorRoles must not be empty");
        correlationId = text(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt);
        reasonCode = controlled(reasonCode, "reasonCode");
        fromStatus = fromStatus == null ? null : controlled(fromStatus, "fromStatus");
        toStatus = controlled(toStatus, "toStatus");
        retentionClass = controlled(retentionClass, "retentionClass");
    }

    public static AuditRecord byActor(
            UUID id, String type, UUID aggregateId, String action, ActorContext actor,
            UUID providerId, String correlationId, Instant occurredAt,
            String reason, String fromStatus, String toStatus) {
        Objects.requireNonNull(actor);
        return new AuditRecord(
                id, type, aggregateId, action, actor.subject(),
                actor.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                providerId, correlationId, occurredAt, reason, fromStatus, toStatus, "AUDIT_EVIDENCE");
    }

    public static AuditRecord bySystemEvent(
            UUID id, String type, UUID aggregateId, String action, UUID providerId,
            String correlationId, Instant occurredAt, String fromStatus, String toStatus) {
        return new AuditRecord(
                id, type, aggregateId, action, "kafka:authorization-service",
                Set.of("SYSTEM_EVENT"), providerId, correlationId, occurredAt,
                "INTEGRATION_EVENT", fromStatus, toStatus, "AUDIT_EVIDENCE");
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static String controlled(String value, String name) {
        String normalized = text(value, name);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,99}")) {
            throw new IllegalArgumentException(name + " must be a controlled value");
        }
        return normalized;
    }
}
