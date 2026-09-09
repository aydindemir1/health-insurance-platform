package com.aydindemir.health.policy.infrastructure.audit;

import com.aydindemir.health.policy.application.audit.AuditRecord;
import com.aydindemir.health.policy.application.dto.PageResult;
import com.aydindemir.health.policy.application.port.out.AuditRecordQuery;
import com.aydindemir.health.policy.application.query.AuditRecordSearchCriteria;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;

@Repository
class PostgresAuditRecordQuery implements AuditRecordQuery {
    private static final String SELECT = """
            select audit_id, aggregate_type, aggregate_id, action, actor_subject, actor_roles,
                   correlation_id, occurred_at, reason_code,
                   changes ->> 'fromStatus' as from_status,
                   changes ->> 'toStatus' as to_status, retention_class
              from audit_records
            """;
    private final NamedParameterJdbcTemplate jdbc;

    PostgresAuditRecordQuery(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public PageResult<AuditRecord> search(AuditRecordSearchCriteria criteria) {
        var parameters = new MapSqlParameterSource()
                .addValue("limit", criteria.size())
                .addValue("offset", criteria.page() * criteria.size());
        var conditions = new java.util.ArrayList<String>();
        if (criteria.aggregateId() != null) {
            conditions.add("aggregate_id = :aggregateId");
            parameters.addValue("aggregateId", criteria.aggregateId());
        }
        if (criteria.action() != null) {
            conditions.add("action = :action");
            parameters.addValue("action", criteria.action());
        }
        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        Long total = jdbc.queryForObject(
                "select count(*) from audit_records" + where, parameters, Long.class);
        var content = jdbc.query(
                SELECT + where + " order by occurred_at desc, audit_id desc limit :limit offset :offset",
                parameters,
                (result, rowNumber) -> new AuditRecord(
                        result.getObject("audit_id", java.util.UUID.class),
                        result.getString("aggregate_type"),
                        result.getObject("aggregate_id", java.util.UUID.class),
                        result.getString("action"), result.getString("actor_subject"),
                        Set.copyOf(Arrays.asList(result.getString("actor_roles").split(","))),
                        result.getString("correlation_id"),
                        result.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        result.getString("reason_code"), result.getString("from_status"),
                        result.getString("to_status"), result.getString("retention_class")));
        long count = total == null ? 0 : total;
        return new PageResult<>(content, criteria.page(), criteria.size(), count,
                (int) Math.ceil((double) count / criteria.size()));
    }
}
