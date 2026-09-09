package com.aydindemir.health.policy.application.query;

import com.aydindemir.health.policy.application.security.ActorContext;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record SearchAuditRecordsQuery(
        ActorContext actor, UUID aggregateId, String action, int page, int size) {
    public static final int MAX_PAGE_SIZE = 100;

    public SearchAuditRecordsQuery {
        Objects.requireNonNull(actor);
        action = normalizeAction(action);
        if (page < 0) throw new IllegalArgumentException("Page must be zero or greater");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public static SearchAuditRecordsQuery fromRequest(
            ActorContext actor, UUID aggregateId, String action, int page, int size) {
        return new SearchAuditRecordsQuery(actor, aggregateId, action, page, size);
    }

    private static String normalizeAction(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"POLICY_ISSUED".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported policy audit action: " + value);
        }
        return normalized;
    }
}
