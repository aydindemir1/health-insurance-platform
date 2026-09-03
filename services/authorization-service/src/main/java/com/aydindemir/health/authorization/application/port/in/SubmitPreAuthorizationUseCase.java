package com.aydindemir.health.authorization.application.port.in;

import com.aydindemir.health.authorization.application.command.SubmitPreAuthorizationCommand;
import com.aydindemir.health.authorization.application.dto.PreAuthorizationResult;

public interface SubmitPreAuthorizationUseCase {
    PreAuthorizationResult submit(SubmitPreAuthorizationCommand command);
}
