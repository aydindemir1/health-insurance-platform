package com.aydindemir.health.authorization.infrastructure.audit;

import com.aydindemir.health.authorization.application.audit.AuditRecord;
import com.aydindemir.health.authorization.application.port.out.AuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
class PostgresAuditTrail implements AuditTrail {
    private static final String INSERT = """
            insert into audit_records (
                audit_id, aggregate_type, aggregate_id, action,
                actor_subject, actor_roles, provider_id, correlation_id,
                occurred_at, reason_code, changes, retention_class
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
            """;

    private final JdbcTemplate jdbc;

    PostgresAuditTrail(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void append(AuditRecord record) {
        Objects.requireNonNull(record);
        jdbc.update(INSERT,
                record.auditId(), record.aggregateType(), record.aggregateId(), record.action().name(),
                record.actorSubject(), serializedRoles(record), record.providerId(), record.correlationId(),
                OffsetDateTime.ofInstant(record.occurredAt(), ZoneOffset.UTC),
                record.reasonCode().name(), serializedChanges(record),
                record.retentionClass());
    }

    private String serializedRoles(AuditRecord record) {
        return record.actorRoles().stream().sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(","));
    }

    private String serializedChanges(AuditRecord record) {
        String fromStatus = record.changes().fromStatus() == null
                ? "null" : "\"" + record.changes().fromStatus() + "\"";
        return "{\"fromStatus\":" + fromStatus
                + ",\"toStatus\":\"" + record.changes().toStatus() + "\"}";
    }
}
