package com.aydindemir.health.claims.infrastructure.messaging;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface SpringDataClaimSearchOutboxRepository extends JpaRepository<ClaimSearchOutboxJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from ClaimSearchOutboxJpaEntity message "
            + "where message.publishedAt is null order by message.occurredAt, message.id")
    List<ClaimSearchOutboxJpaEntity> findUnpublished(Pageable pageable);
}
