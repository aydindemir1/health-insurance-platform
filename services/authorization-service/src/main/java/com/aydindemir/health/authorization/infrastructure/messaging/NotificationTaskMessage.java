package com.aydindemir.health.authorization.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

record NotificationTaskMessage(
        UUID taskId,
        UUID causationId,
        int taskVersion,
        String notificationType,
        UUID businessReferenceId,
        String recipientKind,
        UUID recipientReferenceId,
        String templateKey,
        Instant occurredAt) {

    static NotificationTaskMessage from(NotificationTaskOutboxJpaEntity task) {
        return new NotificationTaskMessage(
                task.taskId,
                task.causationId,
                task.taskVersion,
                task.notificationType.name(),
                task.businessReferenceId,
                task.recipientKind.name(),
                task.recipientReferenceId,
                task.templateKey,
                task.occurredAt);
    }
}
