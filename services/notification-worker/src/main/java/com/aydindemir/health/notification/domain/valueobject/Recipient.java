package com.aydindemir.health.notification.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record Recipient(RecipientKind kind, UUID referenceId) {
    public Recipient {
        Objects.requireNonNull(kind, "kind is required");
        Objects.requireNonNull(referenceId, "referenceId is required");
    }

    public enum RecipientKind {
        PROVIDER
    }
}
