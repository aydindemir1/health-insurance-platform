package com.aydindemir.health.claims.application.port.out;

import com.aydindemir.health.claims.domain.model.Claim;

import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository {
    Claim save(Claim claim);

    Optional<Claim> findById(UUID id);

    Optional<Claim> findByPreAuthorizationId(UUID preAuthorizationId);

    boolean existsByPreAuthorizationId(UUID preAuthorizationId);
}
