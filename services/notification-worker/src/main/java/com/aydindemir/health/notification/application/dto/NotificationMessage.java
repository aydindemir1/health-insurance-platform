package com.aydindemir.health.notification.application.dto;

import com.aydindemir.health.notification.domain.model.NotificationType;
import com.aydindemir.health.notification.domain.valueobject.Recipient;

import java.util.UUID;

public record NotificationMessage(
        UUID idempotencyKey,
        UUID businessReferenceId,
        NotificationType type,
        Recipient recipient,
        String templateKey) {
}
