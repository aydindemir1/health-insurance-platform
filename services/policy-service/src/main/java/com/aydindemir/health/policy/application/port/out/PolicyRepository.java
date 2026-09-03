package com.aydindemir.health.policy.application.port.out;

import com.aydindemir.health.policy.domain.model.Policy;

import java.util.Optional;

public interface PolicyRepository {
    Policy save(Policy policy);

    Optional<Policy> findByPolicyNumber(String policyNumber);

    boolean existsByPolicyNumber(String policyNumber);
}
