package com.aydindemir.health.authorization.application.query;

import com.aydindemir.health.authorization.application.security.ActorContext;

import java.util.Objects;

public record ExportSearchProjectionsQuery(ActorContext actor, int page, int size) {
    public ExportSearchProjectionsQuery {
        Objects.requireNonNull(actor);
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (size < 1 || size > 200) throw new IllegalArgumentException("size must be between 1 and 200");
    }
}
