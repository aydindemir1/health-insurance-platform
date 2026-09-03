package com.aydindemir.health.authorization.application;

import com.aydindemir.health.authorization.domain.PreAuthorization;

import java.util.Optional;
import java.util.UUID;

public interface PreAuthorizationRepository {
    PreAuthorization save(PreAuthorization preAuthorization);
    Optional<PreAuthorization> findById(UUID id);
}
