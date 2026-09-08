package com.aydindemir.health.notification.application.usecase;

import com.aydindemir.health.notification.application.command.DeliverNotificationCommand;
import com.aydindemir.health.notification.application.dto.NotificationMessage;
import com.aydindemir.health.notification.application.port.out.NotificationDeliveryRepository;
import com.aydindemir.health.notification.domain.model.NotificationDelivery;
import com.aydindemir.health.notification.domain.model.NotificationStatus;
import com.aydindemir.health.notification.domain.model.NotificationType;
import com.aydindemir.health.notification.domain.valueobject.Recipient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-08T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void deliversAndRecordsANewTask() {
        var repository = new InMemoryRepository();
        var sent = new AtomicReference<NotificationMessage>();
        var service = new NotificationDeliveryService(repository, sent::set, CLOCK);
        var command = command();

        service.deliver(command);

        assertThat(sent.get().idempotencyKey()).isEqualTo(command.taskId());
        assertThat(repository.findByTaskId(command.taskId()).orElseThrow().status())
                .isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void ignoresAnAlreadyDeliveredTask() {
        var repository = new InMemoryRepository();
        var sendCount = new int[] { 0 };
        var service = new NotificationDeliveryService(repository, message -> sendCount[0]++, CLOCK);
        var command = command();

        service.deliver(command);
        service.deliver(command);

        assertThat(sendCount[0]).isEqualTo(1);
    }

    private DeliverNotificationCommand command() {
        return new DeliverNotificationCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.PRE_AUTHORIZATION_APPROVED,
                new Recipient(Recipient.RecipientKind.PROVIDER, UUID.randomUUID()),
                "pre-authorization-approved-v1");
    }

    private static final class InMemoryRepository implements NotificationDeliveryRepository {
        private final Map<UUID, NotificationDelivery> entries = new HashMap<>();

        @Override
        public Optional<NotificationDelivery> findByTaskId(UUID taskId) {
            return Optional.ofNullable(entries.get(taskId));
        }

        @Override
        public NotificationDelivery save(NotificationDelivery delivery) {
            entries.put(delivery.taskId(), delivery);
            return delivery;
        }
    }
}
