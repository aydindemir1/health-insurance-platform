package com.aydindemir.health.authorization.application.query;

import com.aydindemir.health.authorization.application.security.ActorContext;
import com.aydindemir.health.authorization.domain.model.PreAuthorizationStatus;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record SearchPreAuthorizationsQuery(
        ActorContext actor,
        PreAuthorizationStatus status,
        UUID memberId,
        String policyNumber,
        int page,
        int size,
        SortField sortBy,
        SortDirection direction) {

    public static final int MAX_PAGE_SIZE = 100;

    public SearchPreAuthorizationsQuery {
        actor = Objects.requireNonNull(actor);
        policyNumber = normalize(policyNumber);
        sortBy = Objects.requireNonNull(sortBy);
        direction = Objects.requireNonNull(direction);
        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public static SearchPreAuthorizationsQuery fromRequest(
            ActorContext actor,
            String status,
            UUID memberId,
            String policyNumber,
            int page,
            int size,
            String sortBy,
            String direction) {
        return new SearchPreAuthorizationsQuery(
                actor,
                parseStatus(status),
                memberId,
                policyNumber,
                page,
                size,
                SortField.fromExternalValue(sortBy),
                SortDirection.fromExternalValue(direction));
    }

    private static PreAuthorizationStatus parseStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return PreAuthorizationStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported pre-authorization status: " + value);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public enum SortField {
        CREATED_AT("createdAt"),
        REQUESTED_AMOUNT("requestedAmount"),
        STATUS("status");

        private final String externalValue;

        SortField(String externalValue) {
            this.externalValue = externalValue;
        }

        public static SortField fromExternalValue(String value) {
            for (var field : values()) {
                if (field.externalValue.equalsIgnoreCase(value)) {
                    return field;
                }
            }
            throw new IllegalArgumentException("Unsupported sort field: " + value);
        }
    }

    public enum SortDirection {
        ASC,
        DESC;

        public static SortDirection fromExternalValue(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new IllegalArgumentException("Unsupported sort direction: " + value);
            }
        }
    }
}
