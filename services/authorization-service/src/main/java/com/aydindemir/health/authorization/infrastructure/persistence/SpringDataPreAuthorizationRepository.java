package com.aydindemir.health.authorization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

interface SpringDataPreAuthorizationRepository
        extends JpaRepository<PreAuthorizationJpaEntity, UUID>,
        JpaSpecificationExecutor<PreAuthorizationJpaEntity> {
}
