package com.aydindemir.health.authorization.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataPreAuthorizationRepository
        extends JpaRepository<PreAuthorizationJpaEntity, UUID> {
}
