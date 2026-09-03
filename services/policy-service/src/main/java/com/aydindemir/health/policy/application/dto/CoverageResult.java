package com.aydindemir.health.policy.application.dto;

import java.math.BigDecimal;

public record CoverageResult(
        String serviceCode,
        BigDecimal limit,
        BigDecimal used,
        BigDecimal remaining,
        String currency) {
}
