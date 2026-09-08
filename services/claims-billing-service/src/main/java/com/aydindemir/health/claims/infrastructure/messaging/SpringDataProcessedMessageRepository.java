package com.aydindemir.health.claims.infrastructure.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataProcessedMessageRepository
        extends JpaRepository<ProcessedMessageJpaEntity, UUID> {
}
