package com.aydindemir.health.authorization.application.audit;

import com.aydindemir.health.authorization.application.security.ActorContext;
import com.aydindemir.health.authorization.domain.model.PreAuthorizationStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuditRecord(
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

    public static final String PRE_AUTHORIZATION = "PRE_AUTHORIZATION";
    public static final String AUDIT_EVIDENCE = "AUDIT_EVIDENCE";

    public AuditRecord {
        Objects.requireNonNull(auditId);
        aggregateType = requireText(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(action);
        actorSubject = requireText(actorSubject, "actorSubject");
        actorRoles = Set.copyOf(Objects.requireNonNull(actorRoles));
        if (actorRoles.isEmpty()) {
            throw new IllegalArgumentException("actorRoles must not be empty");
        }
        correlationId = requireText(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(reasonCode);
        Objects.requireNonNull(changes);
        retentionClass = requireText(retentionClass, "retentionClass");
    }

    public static AuditRecord submitted(
            UUID auditId,
            UUID preAuthorizationId,
            ActorContext actor,
            String correlationId,
            Instant occurredAt) {
        return create(auditId, preAuthorizationId, AuditAction.PRE_AUTHORIZATION_SUBMITTED,
                actor, correlationId, occurredAt, AuditReasonCode.USER_SUBMISSION,
                new AuditChanges(null, PreAuthorizationStatus.PENDING.name()));
    }

    public static AuditRecord decided(
            UUID auditId,
            UUID preAuthorizationId,
            PreAuthorizationStatus status,
            ActorContext actor,
            String correlationId,
            Instant occurredAt) {
        AuditAction action = switch (Objects.requireNonNull(status)) {
            case APPROVED -> AuditAction.PRE_AUTHORIZATION_APPROVED;
            case REJECTED -> AuditAction.PRE_AUTHORIZATION_REJECTED;
            default -> throw new IllegalArgumentException("A pending pre-authorization is not a decision");
        };
        return create(auditId, preAuthorizationId, action, actor, correlationId, occurredAt,
                AuditReasonCode.SPECIALIST_DECISION,
                new AuditChanges(PreAuthorizationStatus.PENDING.name(), status.name()));
    }

    private static AuditRecord create(
            UUID auditId,
            UUID aggregateId,
            AuditAction action,
            ActorContext actor,
            String correlationId,
            Instant occurredAt,
            AuditReasonCode reasonCode,
            AuditChanges changes) {
        Objects.requireNonNull(actor);
        return new AuditRecord(
                auditId, PRE_AUTHORIZATION, aggregateId, action, actor.subject(),
                actor.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                actor.providerId(), correlationId, occurredAt, reasonCode, changes, AUDIT_EVIDENCE);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
