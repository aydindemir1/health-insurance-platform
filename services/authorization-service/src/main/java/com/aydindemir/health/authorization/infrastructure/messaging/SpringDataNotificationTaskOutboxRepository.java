package com.aydindemir.health.authorization.infrastructure.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataNotificationTaskOutboxRepository
        extends JpaRepository<NotificationTaskOutboxJpaEntity, UUID> {
}
