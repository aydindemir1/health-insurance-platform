package com.aydindemir.health.authorization.application.query;

import com.aydindemir.health.authorization.application.audit.AuditAction;
import com.aydindemir.health.authorization.application.security.ActorContext;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record SearchAuditRecordsQuery(
        ActorContext actor,
        UUID aggregateId,
        AuditAction action,
        int page,
        int size) {

    public static final int MAX_PAGE_SIZE = 100;

    public SearchAuditRecordsQuery {
        Objects.requireNonNull(actor);
        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public static SearchAuditRecordsQuery fromRequest(
            ActorContext actor, UUID aggregateId, String action, int page, int size) {
        return new SearchAuditRecordsQuery(actor, aggregateId, parseAction(action), page, size);
    }

    private static AuditAction parseAction(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AuditAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported audit action: " + value);
        }
    }
}
