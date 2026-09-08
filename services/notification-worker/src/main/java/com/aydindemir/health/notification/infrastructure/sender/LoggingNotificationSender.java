package com.aydindemir.health.notification.infrastructure.sender;

import com.aydindemir.health.notification.application.dto.NotificationMessage;
import com.aydindemir.health.notification.application.port.out.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.notification.sender.mode",
        havingValue = "log",
        matchIfMissing = true)
class LoggingNotificationSender implements NotificationSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(NotificationMessage message) {
        LOGGER.info(
                "Local notification accepted: taskId={}, notificationType={}, recipientKind={}, recipientReferenceId={}",
                message.idempotencyKey(),
                message.type(),
                message.recipient().kind(),
                message.recipient().referenceId());
    }
}
