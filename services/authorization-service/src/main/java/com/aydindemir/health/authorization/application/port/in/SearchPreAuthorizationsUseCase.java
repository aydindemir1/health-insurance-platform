package com.aydindemir.health.authorization.application.port.in;

import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.dto.PreAuthorizationResult;
import com.aydindemir.health.authorization.application.query.SearchPreAuthorizationsQuery;

public interface SearchPreAuthorizationsUseCase {
    PageResult<PreAuthorizationResult> search(SearchPreAuthorizationsQuery query);
}
