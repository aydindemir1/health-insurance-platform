package com.aydindemir.health.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataNotificationDeliveryRepository
        extends JpaRepository<NotificationDeliveryJpaEntity, UUID> {
}
