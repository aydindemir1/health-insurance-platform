package com.aydindemir.health.notification.application.port.in;

import com.aydindemir.health.notification.application.command.DeliverNotificationCommand;

public interface DeliverNotificationUseCase {
    void deliver(DeliverNotificationCommand command);
}
