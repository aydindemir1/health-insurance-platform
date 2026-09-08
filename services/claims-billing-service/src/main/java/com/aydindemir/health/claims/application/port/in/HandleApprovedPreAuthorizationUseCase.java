package com.aydindemir.health.claims.application.port.in;

import com.aydindemir.health.claims.application.command.HandleApprovedPreAuthorizationCommand;

public interface HandleApprovedPreAuthorizationUseCase {
    void handle(HandleApprovedPreAuthorizationCommand command);
}
