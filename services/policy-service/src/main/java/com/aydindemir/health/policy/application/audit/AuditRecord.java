package com.aydindemir.health.policy.application.audit;

import com.aydindemir.health.policy.application.security.ActorContext;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuditRecord(
        UUID auditId,
        String aggregateType,
        UUID aggregateId,
        String action,
        String actorSubject,
        Set<String> actorRoles,
        String correlationId,
        Instant occurredAt,
        String reasonCode,
        String fromStatus,
        String toStatus,
        String retentionClass) {

    public AuditRecord {
        Objects.requireNonNull(auditId);
        Objects.requireNonNull(aggregateId);
        aggregateType = requireText(aggregateType, "aggregateType");
        action = requireControlled(action, "action");
        actorSubject = requireText(actorSubject, "actorSubject");
        actorRoles = Set.copyOf(Objects.requireNonNull(actorRoles));
        if (actorRoles.isEmpty()) throw new IllegalArgumentException("actorRoles must not be empty");
        correlationId = requireText(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt);
        reasonCode = requireControlled(reasonCode, "reasonCode");
        fromStatus = fromStatus == null ? null : requireControlled(fromStatus, "fromStatus");
        toStatus = requireControlled(toStatus, "toStatus");
        retentionClass = requireControlled(retentionClass, "retentionClass");
    }

    public static AuditRecord policyIssued(
            UUID auditId, UUID policyId, ActorContext actor, String correlationId, Instant occurredAt) {
        Objects.requireNonNull(actor);
        return new AuditRecord(
                auditId, "POLICY", policyId, "POLICY_ISSUED", actor.subject(),
                actor.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                correlationId, occurredAt, "USER_CREATION", null, "ACTIVE", "AUDIT_EVIDENCE");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static String requireControlled(String value, String name) {
        String normalized = requireText(value, name);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,99}")) {
            throw new IllegalArgumentException(name + " must be a controlled value");
        }
        return normalized;
    }
}
