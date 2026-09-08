package com.aydindemir.health.notification.infrastructure.messaging;

final class NotificationRabbitContract {
    static final String EXCHANGE = "health.notifications";
    static final String ROUTING_KEY = "pre-authorization.decision";
    static final String DELIVERY_QUEUE = "health.notifications.delivery.v1";
    static final String DEAD_LETTER_EXCHANGE = "health.notifications.dlx";
    static final String DEAD_LETTER_QUEUE = "health.notifications.delivery.v1.dlq";
    static final String DEAD_LETTER_ROUTING_KEY = "dead-letter";
    static final int SUPPORTED_TASK_VERSION = 1;

    private NotificationRabbitContract() {
    }
}
