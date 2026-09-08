package com.aydindemir.health.search.application.query;

import com.aydindemir.health.search.application.security.ActorContext;
import java.util.UUID;

public record SearchRecordsQuery(
        ActorContext actor, String text, String type, String status,
        UUID providerId, int page, int size) {
    public SearchRecordsQuery {
        if (actor == null) throw new IllegalArgumentException("actor is required");
        text = normalize(text);
        type = normalize(type);
        status = normalize(status);
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
