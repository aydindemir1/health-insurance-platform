package com.aydindemir.health.policy.infrastructure.configuration;

import com.aydindemir.health.policy.application.command.CreatePolicyCommand;
import com.aydindemir.health.policy.application.port.in.CreatePolicyUseCase;
import com.aydindemir.health.policy.application.port.in.SearchAuditRecordsUseCase;
import com.aydindemir.health.policy.application.query.SearchAuditRecordsQuery;
import com.aydindemir.health.policy.application.port.out.AuditTrail;
import com.aydindemir.health.policy.application.port.out.CoverageEvaluationCache;
import com.aydindemir.health.policy.application.security.ActorContext;
import com.aydindemir.health.policy.application.security.ApplicationRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@Testcontainers
@SpringBootTest
class PolicyAuditTransactionIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired CreatePolicyUseCase createPolicy;
    @Autowired JdbcTemplate jdbc;
    @Autowired SearchAuditRecordsUseCase searchAudit;
    @MockitoBean CoverageEvaluationCache coverageCache;
    @MockitoSpyBean AuditTrail auditTrail;

    @Test
    void commitsPolicyAndMinimizedAuditTogether() {
        var created = createPolicy.create(command("POL-AUDIT-1"));

        assertThat(jdbc.queryForMap(
                "select aggregate_type, aggregate_id, action, actor_subject, actor_roles, "
                        + "reason_code, changes::text as changes from audit_records where aggregate_id = ?",
                created.id()))
                .containsEntry("aggregate_type", "POLICY")
                .containsEntry("aggregate_id", created.id())
                .containsEntry("action", "POLICY_ISSUED")
                .containsEntry("actor_subject", "policy-specialist")
                .containsEntry("actor_roles", "INSURANCE_SPECIALIST")
                .containsEntry("reason_code", "USER_CREATION");
        assertThat(jdbc.queryForObject(
                "select changes::text from audit_records where aggregate_id = ?",
                String.class, created.id()))
                .doesNotContain("POL-AUDIT-1", "IMG-MRI", "10000.00");
    }

    @Test
    void rollsBackPolicyWhenAuditCannotBeStored() {
        doThrow(new IllegalStateException("simulated audit persistence failure"))
                .when(auditTrail).append(any());

        assertThatThrownBy(() -> createPolicy.create(command("POL-AUDIT-ROLLBACK")))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "select count(*) from policies where policy_number = 'POL-AUDIT-ROLLBACK'",
                Integer.class)).isZero();
    }

    @Test
    void databaseRejectsAuditMutation() {
        var created = createPolicy.create(command("POL-AUDIT-IMMUTABLE"));

        assertThatThrownBy(() -> jdbc.update(
                "update audit_records set actor_subject = 'tampered' where aggregate_id = ?", created.id()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "delete from audit_records where aggregate_id = ?", created.id()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate table audit_records"))
                .hasMessageContaining("append-only");
    }

    @Test
    void readsFilteredPolicyAuditThroughAdministratorBoundary() {
        var created = createPolicy.create(command("POL-AUDIT-READ"));

        var result = searchAudit.search(SearchAuditRecordsQuery.fromRequest(
                new ActorContext("administrator", Set.of(ApplicationRole.SYSTEM_ADMIN)),
                created.id(), "POLICY_ISSUED", 0, 10));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement().satisfies(record -> {
            assertThat(record.aggregateId()).isEqualTo(created.id());
            assertThat(record.action()).isEqualTo("POLICY_ISSUED");
            assertThat(record.fromStatus()).isNull();
            assertThat(record.toStatus()).isEqualTo("ACTIVE");
        });
    }

    private CreatePolicyCommand command(String policyNumber) {
        return new CreatePolicyCommand(
                new ActorContext("policy-specialist", Set.of(ApplicationRole.INSURANCE_SPECIALIST)),
                policyNumber, UUID.randomUUID(), LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-12-31"),
                List.of(new CreatePolicyCommand.CoverageDefinition(
                        "IMG-MRI", new BigDecimal("10000.00"), Currency.getInstance("TRY"))));
    }
}
