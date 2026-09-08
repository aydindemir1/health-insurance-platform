package com.aydindemir.health.authorization.application.port.out;

import com.aydindemir.health.authorization.application.event.PreAuthorizationNotificationTask;

public interface NotificationTaskOutbox {
    void append(PreAuthorizationNotificationTask task);
}
