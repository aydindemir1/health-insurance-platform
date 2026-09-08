package com.aydindemir.health.authorization.infrastructure.messaging;

import com.aydindemir.health.authorization.application.event.PreAuthorizationNotificationTask;
import com.aydindemir.health.authorization.application.port.out.NotificationTaskOutbox;
import org.springframework.stereotype.Component;

@Component
class JpaNotificationTaskOutbox implements NotificationTaskOutbox {
    private final SpringDataNotificationTaskOutboxRepository repository;

    JpaNotificationTaskOutbox(SpringDataNotificationTaskOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(PreAuthorizationNotificationTask task) {
        repository.save(new NotificationTaskOutboxJpaEntity(task));
    }
}
