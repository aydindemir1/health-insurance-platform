package com.aydindemir.health.claims.infrastructure.audit;

import com.aydindemir.health.claims.application.audit.AuditRecord;
import com.aydindemir.health.claims.application.port.out.AuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.stream.Collectors;

@Repository
class PostgresAuditTrail implements AuditTrail {
    private static final String INSERT = """
            insert into audit_records (
                audit_id, aggregate_type, aggregate_id, action, actor_subject, actor_roles,
                provider_id, correlation_id, occurred_at, reason_code, changes, retention_class
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
            """;
    private final JdbcTemplate jdbc;

    PostgresAuditTrail(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void append(AuditRecord record) {
        String roles = record.actorRoles().stream().sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(","));
        String changes = "{\"fromStatus\":"
                + (record.fromStatus() == null ? "null" : "\"" + record.fromStatus() + "\"")
                + ",\"toStatus\":\"" + record.toStatus() + "\"}";
        jdbc.update(INSERT,
                record.auditId(), record.aggregateType(), record.aggregateId(), record.action(),
                record.actorSubject(), roles, record.providerId(), record.correlationId(),
                OffsetDateTime.ofInstant(record.occurredAt(), ZoneOffset.UTC), record.reasonCode(),
                changes, record.retentionClass());
    }
}
