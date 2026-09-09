package com.aydindemir.health.authorization.infrastructure.audit;

import com.aydindemir.health.authorization.application.audit.AuditAction;
import com.aydindemir.health.authorization.application.audit.AuditChanges;
import com.aydindemir.health.authorization.application.audit.AuditReasonCode;
import com.aydindemir.health.authorization.application.audit.AuditRecord;
import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.port.out.AuditRecordQuery;
import com.aydindemir.health.authorization.application.query.AuditRecordSearchCriteria;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;

@Repository
class PostgresAuditRecordQuery implements AuditRecordQuery {
    private static final String SELECT = """
            select audit_id, aggregate_type, aggregate_id, action,
                   actor_subject, actor_roles, provider_id, correlation_id,
                   occurred_at, reason_code,
                   changes ->> 'fromStatus' as from_status,
                   changes ->> 'toStatus' as to_status,
                   retention_class
              from audit_records
            """;

    private final NamedParameterJdbcTemplate jdbc;

    PostgresAuditRecordQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<AuditRecord> search(AuditRecordSearchCriteria criteria) {
        var parameters = new MapSqlParameterSource()
                .addValue("limit", criteria.size())
                .addValue("offset", criteria.page() * criteria.size());
        String where = whereClause(criteria, parameters);
        Long total = jdbc.queryForObject(
                "select count(*) from audit_records" + where, parameters, Long.class);
        var content = jdbc.query(
                SELECT + where + " order by occurred_at desc, audit_id desc limit :limit offset :offset",
                parameters,
                rowMapper());
        long totalElements = total == null ? 0 : total;
        int totalPages = (int) Math.ceil((double) totalElements / criteria.size());
        return new PageResult<>(content, criteria.page(), criteria.size(), totalElements, totalPages);
    }

    private String whereClause(
            AuditRecordSearchCriteria criteria, MapSqlParameterSource parameters) {
        var conditions = new java.util.ArrayList<String>();
        if (criteria.aggregateId() != null) {
            conditions.add("aggregate_id = :aggregateId");
            parameters.addValue("aggregateId", criteria.aggregateId());
        }
        if (criteria.action() != null) {
            conditions.add("action = :action");
            parameters.addValue("action", criteria.action().name());
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    private RowMapper<AuditRecord> rowMapper() {
        return (result, rowNumber) -> new AuditRecord(
                result.getObject("audit_id", java.util.UUID.class),
                result.getString("aggregate_type"),
                result.getObject("aggregate_id", java.util.UUID.class),
                AuditAction.valueOf(result.getString("action")),
                result.getString("actor_subject"),
                roles(result),
                result.getObject("provider_id", java.util.UUID.class),
                result.getString("correlation_id"),
                result.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                AuditReasonCode.valueOf(result.getString("reason_code")),
                new AuditChanges(result.getString("from_status"), result.getString("to_status")),
                result.getString("retention_class"));
    }

    private Set<String> roles(ResultSet result) throws SQLException {
        return Set.copyOf(Arrays.asList(result.getString("actor_roles").split(",")));
    }
}
