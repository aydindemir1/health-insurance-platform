package com.aydindemir.health.authorization.application.port.in;

import com.aydindemir.health.authorization.application.dto.PreAuthorizationResult;
import com.aydindemir.health.authorization.application.query.GetPreAuthorizationQuery;

public interface GetPreAuthorizationUseCase {
    PreAuthorizationResult get(GetPreAuthorizationQuery query);
}
