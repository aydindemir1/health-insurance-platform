package com.aydindemir.health.policy.domain.valueobject;

import java.util.Locale;

public record ServiceCode(String value) {
    public ServiceCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Service code must not be blank");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.length() > 40) {
            throw new IllegalArgumentException("Service code must not exceed 40 characters");
        }
    }
}
