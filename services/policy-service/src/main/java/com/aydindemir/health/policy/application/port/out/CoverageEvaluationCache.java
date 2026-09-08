package com.aydindemir.health.policy.application.port.out;

import com.aydindemir.health.policy.application.command.EvaluateCoverageCommand;
import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;

import java.util.Optional;

public interface CoverageEvaluationCache {
    Optional<CoverageEvaluationResult> find(EvaluateCoverageCommand command);

    void store(EvaluateCoverageCommand command, CoverageEvaluationResult result);

    void evictPolicy(String policyNumber);
}
