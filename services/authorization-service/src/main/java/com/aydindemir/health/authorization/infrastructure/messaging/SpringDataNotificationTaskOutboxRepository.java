package com.aydindemir.health.authorization.infrastructure.messaging;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface SpringDataNotificationTaskOutboxRepository
        extends JpaRepository<NotificationTaskOutboxJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from NotificationTaskOutboxJpaEntity task "
            + "where task.publishedAt is null order by task.occurredAt, task.taskId")
    List<NotificationTaskOutboxJpaEntity> findUnpublished(Pageable pageable);
}
