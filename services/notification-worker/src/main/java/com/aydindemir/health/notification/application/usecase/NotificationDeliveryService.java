package com.aydindemir.health.notification.application.usecase;

import com.aydindemir.health.notification.application.command.DeliverNotificationCommand;
import com.aydindemir.health.notification.application.dto.NotificationMessage;
import com.aydindemir.health.notification.application.port.in.DeliverNotificationUseCase;
import com.aydindemir.health.notification.application.port.out.NotificationDeliveryRepository;
import com.aydindemir.health.notification.application.port.out.NotificationSender;
import com.aydindemir.health.notification.domain.model.NotificationDelivery;
import com.aydindemir.health.notification.domain.model.NotificationStatus;

import java.time.Clock;
import java.util.Objects;

public final class NotificationDeliveryService implements DeliverNotificationUseCase {
    private final NotificationDeliveryRepository deliveries;
    private final NotificationSender sender;
    private final Clock clock;

    public NotificationDeliveryService(
            NotificationDeliveryRepository deliveries,
            NotificationSender sender,
            Clock clock) {
        this.deliveries = Objects.requireNonNull(deliveries);
        this.sender = Objects.requireNonNull(sender);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void deliver(DeliverNotificationCommand command) {
        Objects.requireNonNull(command);
        var existing = deliveries.findByTaskId(Objects.requireNonNull(command.taskId()));
        if (existing.filter(delivery -> delivery.status() == NotificationStatus.DELIVERED).isPresent()) {
            return;
        }
        NotificationDelivery delivery = existing.orElseGet(() -> NotificationDelivery.receive(
                command.taskId(), command.causationId(), command.businessReferenceId(),
                command.type(), command.recipient(), command.templateKey(), clock));
        deliveries.save(delivery);
        sender.send(new NotificationMessage(
                delivery.taskId(), delivery.businessReferenceId(), delivery.type(),
                delivery.recipient(), delivery.templateKey()));
        delivery.markDelivered(clock);
        deliveries.save(delivery);
    }
}
