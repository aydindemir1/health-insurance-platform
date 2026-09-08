package com.aydindemir.health.notification.infrastructure.messaging;

final class NotificationRabbitContract {
    static final String DELIVERY_QUEUE = "health.notifications.delivery.v1";
    static final int SUPPORTED_TASK_VERSION = 1;

    private NotificationRabbitContract() {
    }
}
