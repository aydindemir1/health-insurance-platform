package com.aydindemir.health.policy.application.port.in;

import com.aydindemir.health.policy.application.command.EvaluateCoverageCommand;
import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;

public interface EvaluateCoverageUseCase {
    CoverageEvaluationResult evaluate(EvaluateCoverageCommand command);
}
