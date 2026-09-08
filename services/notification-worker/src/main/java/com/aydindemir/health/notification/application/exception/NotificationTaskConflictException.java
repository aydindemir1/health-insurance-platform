package com.aydindemir.health.notification.application.exception;

import java.util.UUID;

public final class NotificationTaskConflictException extends RuntimeException {
    public NotificationTaskConflictException(UUID taskId) {
        super("Notification task identifier is already used by a different intent: " + taskId);
    }
}
