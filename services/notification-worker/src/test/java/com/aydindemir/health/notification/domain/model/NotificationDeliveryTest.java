package com.aydindemir.health.notification.domain.model;

import com.aydindemir.health.notification.domain.valueobject.Recipient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationDeliveryTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-08T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void movesReceivedTaskToDelivered() {
        var delivery = delivery();

        delivery.markDelivered(CLOCK);

        assertThat(delivery.status()).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(delivery.deliveredAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void preventsASecondDeliveryTransition() {
        var delivery = delivery();
        delivery.markDelivered(CLOCK);

        assertThatThrownBy(() -> delivery.markDelivered(CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already delivered");
    }

    @Test
    void rejectsAnInconsistentRehydratedState() {
        var delivery = delivery();

        assertThatThrownBy(() -> NotificationDelivery.rehydrate(
                delivery.taskId(), delivery.causationId(), delivery.businessReferenceId(),
                delivery.type(), delivery.recipient(), delivery.templateKey(),
                NotificationStatus.DELIVERED, delivery.receivedAt(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A delivered notification requires a delivery time");
    }

    private NotificationDelivery delivery() {
        return NotificationDelivery.receive(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.PRE_AUTHORIZATION_APPROVED,
                new Recipient(Recipient.RecipientKind.PROVIDER, UUID.randomUUID()),
                "pre-authorization-approved-v1", CLOCK);
    }
}
