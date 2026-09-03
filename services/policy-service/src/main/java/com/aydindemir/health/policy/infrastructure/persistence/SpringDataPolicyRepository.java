package com.aydindemir.health.policy.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataPolicyRepository extends JpaRepository<PolicyJpaEntity, UUID> {
    Optional<PolicyJpaEntity> findByPolicyNumberIgnoreCase(String policyNumber);

    boolean existsByPolicyNumberIgnoreCase(String policyNumber);
}
