package com.aydindemir.health.notification.infrastructure.configuration;

import com.aydindemir.health.notification.application.command.DeliverNotificationCommand;
import com.aydindemir.health.notification.application.port.in.DeliverNotificationUseCase;
import org.springframework.transaction.annotation.Transactional;

final class TransactionalDeliverNotificationUseCase implements DeliverNotificationUseCase {
    private final DeliverNotificationUseCase delegate;

    TransactionalDeliverNotificationUseCase(DeliverNotificationUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void deliver(DeliverNotificationCommand command) {
        delegate.deliver(command);
    }
}
