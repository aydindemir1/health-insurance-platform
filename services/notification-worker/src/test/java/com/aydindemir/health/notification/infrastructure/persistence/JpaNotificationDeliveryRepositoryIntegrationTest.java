package com.aydindemir.health.notification.infrastructure.persistence;

import com.aydindemir.health.notification.application.command.DeliverNotificationCommand;
import com.aydindemir.health.notification.application.port.out.NotificationDeliveryRepository;
import com.aydindemir.health.notification.application.usecase.NotificationDeliveryService;
import com.aydindemir.health.notification.domain.model.NotificationDelivery;
import com.aydindemir.health.notification.domain.model.NotificationStatus;
import com.aydindemir.health.notification.domain.model.NotificationType;
import com.aydindemir.health.notification.domain.valueobject.Recipient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaNotificationDeliveryRepositoryAdapter.class)
class JpaNotificationDeliveryRepositoryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final Clock RECEIVED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-08T18:00:00Z"), ZoneOffset.UTC);
    private static final Clock DELIVERED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-08T18:01:00Z"), ZoneOffset.UTC);

    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired JdbcTemplate jdbc;

    @Test
    void appliesMigrationAndRoundTripsDeliveryState() {
        var delivery = newDelivery();
        deliveries.save(delivery);

        assertThat(deliveries.findByTaskId(delivery.taskId())).hasValueSatisfying(received -> {
            assertThat(received.status()).isEqualTo(NotificationStatus.RECEIVED);
            assertThat(received.deliveredAt()).isNull();
            assertThat(received.recipient()).isEqualTo(delivery.recipient());
        });

        delivery.markDelivered(DELIVERED_CLOCK);
        deliveries.save(delivery);

        assertThat(deliveries.findByTaskId(delivery.taskId())).hasValueSatisfying(delivered -> {
            assertThat(delivered.status()).isEqualTo(NotificationStatus.DELIVERED);
            assertThat(delivered.deliveredAt()).isEqualTo(DELIVERED_CLOCK.instant());
            assertThat(delivered.templateKey()).isEqualTo("pre-authorization-approved-v1");
        });
        assertThat(jdbc.queryForObject("select count(*) from databasechangelog", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void createsIdempotencyPrimaryKeyAndOperationalIndexes() {
        var indexes = jdbc.queryForList(
                "select indexname from pg_indexes where tablename = 'notification_deliveries'",
                String.class);

        assertThat(indexes).contains(
                "notification_deliveries_pkey",
                "idx_notification_deliveries_recipient",
                "idx_notification_deliveries_status");
    }

    @Test
    void suppressesDeliveryWhenTheSameTaskIsReloadedFromPostgresql() {
        var sends = new AtomicInteger();
        var service = new NotificationDeliveryService(
                deliveries, message -> sends.incrementAndGet(), RECEIVED_CLOCK);
        var command = new DeliverNotificationCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.PRE_AUTHORIZATION_REJECTED,
                new Recipient(Recipient.RecipientKind.PROVIDER, UUID.randomUUID()),
                "pre-authorization-rejected-v1");

        service.deliver(command);
        service.deliver(command);

        assertThat(sends).hasValue(1);
        assertThat(deliveries.findByTaskId(command.taskId()))
                .hasValueSatisfying(delivery ->
                        assertThat(delivery.status()).isEqualTo(NotificationStatus.DELIVERED));
    }

    private NotificationDelivery newDelivery() {
        return NotificationDelivery.receive(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.PRE_AUTHORIZATION_APPROVED,
                new Recipient(Recipient.RecipientKind.PROVIDER, UUID.randomUUID()),
                "pre-authorization-approved-v1", RECEIVED_CLOCK);
    }
}
