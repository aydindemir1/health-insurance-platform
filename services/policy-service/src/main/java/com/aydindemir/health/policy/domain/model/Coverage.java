package com.aydindemir.health.policy.domain.model;

import com.aydindemir.health.policy.domain.valueobject.Money;
import com.aydindemir.health.policy.domain.valueobject.ServiceCode;

import java.util.Objects;

public record Coverage(ServiceCode serviceCode, Money limit, Money used) {
    public Coverage {
        Objects.requireNonNull(serviceCode);
        Objects.requireNonNull(limit);
        Objects.requireNonNull(used);
        if (limit.amount().signum() <= 0) {
            throw new IllegalArgumentException("Coverage limit must be positive");
        }
        if (!limit.currency().equals(used.currency())) {
            throw new IllegalArgumentException("Coverage limit and usage currencies must match");
        }
        if (used.isGreaterThan(limit)) {
            throw new IllegalArgumentException("Coverage usage cannot exceed its limit");
        }
    }

    public Money remaining() {
        return limit.subtract(used);
    }

    public Coverage recordUtilization(Money amount) {
        Objects.requireNonNull(amount);
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Coverage utilization must be positive");
        }
        Money nextUsed = used.add(amount);
        if (nextUsed.isGreaterThan(limit)) {
            throw new IllegalArgumentException("Coverage limit would be exceeded");
        }
        return new Coverage(serviceCode, limit, nextUsed);
    }
}
