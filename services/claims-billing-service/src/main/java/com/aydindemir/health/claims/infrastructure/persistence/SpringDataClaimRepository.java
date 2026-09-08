package com.aydindemir.health.claims.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

interface SpringDataClaimRepository extends JpaRepository<ClaimJpaEntity, UUID> {
    boolean existsByPreAuthorizationId(UUID preAuthorizationId);
    Optional<ClaimJpaEntity> findByPreAuthorizationId(UUID preAuthorizationId);
}
