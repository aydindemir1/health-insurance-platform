package com.aydindemir.health.authorization.infrastructure.messaging;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface SpringDataOutboxRepository extends JpaRepository<OutboxMessageJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from OutboxMessageJpaEntity message " +
            "where message.publishedAt is null order by message.occurredAt, message.id")
    List<OutboxMessageJpaEntity> findUnpublished(Pageable pageable);
}
