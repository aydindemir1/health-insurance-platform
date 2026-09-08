package com.aydindemir.health.authorization.infrastructure.configuration;

import com.aydindemir.health.authorization.application.command.DecidePreAuthorizationCommand;
import com.aydindemir.health.authorization.application.command.SubmitPreAuthorizationCommand;
import com.aydindemir.health.authorization.application.port.in.DecidePreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.SubmitPreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.out.CoverageVerificationPort;
import com.aydindemir.health.authorization.application.port.out.NotificationTaskOutbox;
import com.aydindemir.health.authorization.application.security.ActorContext;
import com.aydindemir.health.authorization.application.security.ApplicationRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "app.messaging.outbox.enabled=false",
        "app.messaging.notification-outbox.enabled=false"
})
class DecisionOutboxTransactionIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired SubmitPreAuthorizationUseCase submit;
    @Autowired DecidePreAuthorizationUseCase decide;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean CoverageVerificationPort coverageVerification;
    @MockitoSpyBean NotificationTaskOutbox notificationTaskOutbox;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("delete from notification_task_outbox");
        jdbc.update("delete from outbox_messages");
        jdbc.update("delete from pre_authorizations");
        when(coverageVerification.verify(any())).thenReturn(
                new CoverageVerificationPort.CoverageVerificationResult(
                        true, "ELIGIBLE", "Coverage is eligible"));
    }

    @Test
    void commitsDecisionKafkaEventAndNotificationTaskTogether() {
        UUID id = submitPending();

        decide.approve(new DecidePreAuthorizationCommand(
                id, "Coverage verified", specialist()));

        assertThat(jdbc.queryForObject(
                "select status from pre_authorizations where id = ?", String.class, id))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_messages where aggregate_id = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from notification_task_outbox where business_reference_id = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from notification_task_outbox task "
                        + "join outbox_messages event on event.id = task.causation_id "
                        + "where task.business_reference_id = ?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void rollsBackDecisionAndKafkaEventWhenNotificationIntentCannotBeStored() {
        UUID id = submitPending();
        doThrow(new IllegalStateException("simulated notification outbox failure"))
                .when(notificationTaskOutbox).append(any());

        assertThatThrownBy(() -> decide.approve(new DecidePreAuthorizationCommand(
                id, "Coverage verified", specialist())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated");

        assertThat(jdbc.queryForObject(
                "select status from pre_authorizations where id = ?", String.class, id))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_messages where aggregate_id = ?",
                Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from notification_task_outbox where business_reference_id = ?",
                Integer.class, id)).isZero();
    }

    private UUID submitPending() {
        return submit.submit(new SubmitPreAuthorizationCommand(
                new ActorContext("hospital", UUID.randomUUID(),
                        Set.of(ApplicationRole.HOSPITAL_USER)),
                UUID.randomUUID(), "POL-TX-100", "IMG-MRI", "J18.9",
                new BigDecimal("1250.00"), Currency.getInstance("TRY"))).id();
    }

    private ActorContext specialist() {
        return new ActorContext(
                "specialist", null, Set.of(ApplicationRole.INSURANCE_SPECIALIST));
    }
}
