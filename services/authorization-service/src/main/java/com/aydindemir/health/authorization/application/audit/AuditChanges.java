package com.aydindemir.health.authorization.application.audit;

public record AuditChanges(String fromStatus, String toStatus) {
    public AuditChanges {
        toStatus = requireStatus(toStatus, "toStatus");
        fromStatus = normalizeStatus(fromStatus);
    }

    private static String requireStatus(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException(name + " must be a controlled status value");
        }
        return normalized;
    }

    private static String normalizeStatus(String value) {
        return value == null || value.isBlank() ? null : requireStatus(value, "fromStatus");
    }
}
