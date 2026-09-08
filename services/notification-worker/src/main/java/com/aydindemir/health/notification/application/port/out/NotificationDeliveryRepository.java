package com.aydindemir.health.notification.application.port.out;

import com.aydindemir.health.notification.domain.model.NotificationDelivery;

import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryRepository {
    Optional<NotificationDelivery> findByTaskId(UUID taskId);
    NotificationDelivery save(NotificationDelivery delivery);
}
