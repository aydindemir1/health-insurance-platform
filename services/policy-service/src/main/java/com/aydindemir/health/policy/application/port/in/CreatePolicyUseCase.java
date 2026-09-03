package com.aydindemir.health.policy.application.port.in;

import com.aydindemir.health.policy.application.command.CreatePolicyCommand;
import com.aydindemir.health.policy.application.dto.PolicyResult;

public interface CreatePolicyUseCase {
    PolicyResult create(CreatePolicyCommand command);
}
