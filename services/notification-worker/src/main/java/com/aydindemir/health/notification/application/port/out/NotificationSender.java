package com.aydindemir.health.notification.application.port.out;

import com.aydindemir.health.notification.application.dto.NotificationMessage;

public interface NotificationSender {
    void send(NotificationMessage message);
}
