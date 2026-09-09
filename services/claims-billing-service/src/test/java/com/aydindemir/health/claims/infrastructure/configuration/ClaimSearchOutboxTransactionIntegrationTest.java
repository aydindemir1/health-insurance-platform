package com.aydindemir.health.claims.infrastructure.configuration;

import com.aydindemir.health.claims.application.command.CreateClaimCommand;
import com.aydindemir.health.claims.application.port.in.CreateClaimUseCase;
import com.aydindemir.health.claims.application.port.in.SearchAuditRecordsUseCase;
import com.aydindemir.health.claims.application.query.SearchAuditRecordsQuery;
import com.aydindemir.health.claims.application.port.out.ApprovedPreAuthorizationPort;
import com.aydindemir.health.claims.application.port.out.ClaimSearchProjectionOutbox;
import com.aydindemir.health.claims.application.port.out.AuditTrail;
import com.aydindemir.health.claims.application.security.ActorContext;
import com.aydindemir.health.claims.application.security.ApplicationRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.messaging.consumer.enabled=false",
        "app.messaging.search-outbox.enabled=false"
})
class ClaimSearchOutboxTransactionIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired CreateClaimUseCase createClaim;
    @Autowired SearchAuditRecordsUseCase searchAudit;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ApprovedPreAuthorizationPort preAuthorizations;
    @MockitoSpyBean ClaimSearchProjectionOutbox searchOutbox;
    @MockitoSpyBean AuditTrail auditTrail;

    private UUID preAuthorizationId;
    private UUID providerId;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("delete from claim_search_outbox");
        jdbc.update("delete from invoice_payments");
        jdbc.update("delete from invoices");
        jdbc.update("delete from claims");
        preAuthorizationId = UUID.randomUUID();
        providerId = UUID.randomUUID();
        when(preAuthorizations.findById(preAuthorizationId)).thenReturn(Optional.of(
                new ApprovedPreAuthorizationPort.PreAuthorizationSnapshot(
                        preAuthorizationId, UUID.randomUUID(), providerId, "POL-TX-SEARCH",
                        "IMG-MRI", new BigDecimal("1000.00"), Currency.getInstance("TRY"), "APPROVED")));
    }

    @Test
    void commitsClaimInvoiceAndSearchProjectionTogether() {
        var result = createClaim.create(command());
        assertThat(count("claims")).isEqualTo(1);
        assertThat(count("invoices")).isEqualTo(1);
        assertThat(count("claim_search_outbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_records where aggregate_id in (?, ?)",
                Integer.class, result.claim().id(), result.invoice().id())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select string_agg(action, ',' order by action) from audit_records "
                        + "where aggregate_id in (?, ?)",
                String.class, result.claim().id(), result.invoice().id()))
                .isEqualTo("CLAIM_SUBMITTED,INVOICE_ISSUED");
    }

    @Test
    void rollsBackBusinessDataWhenProjectionCannotBeStored() {
        doThrow(new IllegalStateException("simulated search outbox failure"))
                .when(searchOutbox).append(any());
        assertThatThrownBy(() -> createClaim.create(command()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("simulated");
        assertThat(count("claims")).isZero();
        assertThat(count("invoices")).isZero();
        assertThat(count("claim_search_outbox")).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_records where actor_subject = ?",
                Integer.class, actorSubject())).isZero();
    }

    @Test
    void rollsBackBusinessDataAndProjectionWhenAuditCannotBeStored() {
        doThrow(new IllegalStateException("simulated audit failure"))
                .when(auditTrail).append(any());

        assertThatThrownBy(() -> createClaim.create(command()))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(count("claims")).isZero();
        assertThat(count("invoices")).isZero();
        assertThat(count("claim_search_outbox")).isZero();
    }

    @Test
    void databaseRejectsAuditMutation() {
        var result = createClaim.create(command());

        assertThatThrownBy(() -> jdbc.update(
                "update audit_records set actor_subject = 'tampered' where aggregate_id = ?",
                result.claim().id())).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "delete from audit_records where aggregate_id = ?", result.claim().id()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate table audit_records"))
                .hasMessageContaining("append-only");
    }

    @Test
    void readsFilteredClaimsAuditThroughAdministratorBoundary() {
        var created = createClaim.create(command());

        var result = searchAudit.search(SearchAuditRecordsQuery.fromRequest(
                new ActorContext("administrator", null, Set.of(ApplicationRole.SYSTEM_ADMIN)),
                created.claim().id(), "claim_submitted", 0, 10));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement().satisfies(record -> {
            assertThat(record.aggregateId()).isEqualTo(created.claim().id());
            assertThat(record.action()).isEqualTo("CLAIM_SUBMITTED");
            assertThat(record.providerId()).isEqualTo(providerId);
            assertThat(record.fromStatus()).isNull();
            assertThat(record.toStatus()).isEqualTo("SUBMITTED");
        });
    }

    private CreateClaimCommand command() {
        return new CreateClaimCommand(
                new ActorContext(actorSubject(), providerId, Set.of(ApplicationRole.HOSPITAL_USER)),
                preAuthorizationId, "INV-TX-SEARCH", new BigDecimal("900.00"), Currency.getInstance("TRY"));
    }

    private String actorSubject() {
        return "hospital-" + preAuthorizationId;
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
