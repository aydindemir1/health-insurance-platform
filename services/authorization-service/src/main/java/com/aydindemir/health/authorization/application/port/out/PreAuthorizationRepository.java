package com.aydindemir.health.authorization.application.port.out;

import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.query.PreAuthorizationSearchCriteria;
import com.aydindemir.health.authorization.domain.model.PreAuthorization;

import java.util.Optional;
import java.util.UUID;

public interface PreAuthorizationRepository {
    PreAuthorization save(PreAuthorization preAuthorization);

    Optional<PreAuthorization> findById(UUID id);

    PageResult<PreAuthorization> search(PreAuthorizationSearchCriteria criteria);
}
