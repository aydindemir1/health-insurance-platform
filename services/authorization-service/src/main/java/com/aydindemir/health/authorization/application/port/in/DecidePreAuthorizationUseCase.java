package com.aydindemir.health.authorization.application.port.in;

import com.aydindemir.health.authorization.application.command.DecidePreAuthorizationCommand;
import com.aydindemir.health.authorization.application.dto.PreAuthorizationResult;

public interface DecidePreAuthorizationUseCase {
    PreAuthorizationResult approve(DecidePreAuthorizationCommand command);

    PreAuthorizationResult reject(DecidePreAuthorizationCommand command);
}
