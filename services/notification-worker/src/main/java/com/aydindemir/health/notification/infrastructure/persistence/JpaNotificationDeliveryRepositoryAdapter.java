package com.aydindemir.health.notification.infrastructure.persistence;

import com.aydindemir.health.notification.application.port.out.NotificationDeliveryRepository;
import com.aydindemir.health.notification.domain.model.NotificationDelivery;
import com.aydindemir.health.notification.domain.valueobject.Recipient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaNotificationDeliveryRepositoryAdapter implements NotificationDeliveryRepository {
    private final SpringDataNotificationDeliveryRepository repository;

    JpaNotificationDeliveryRepositoryAdapter(SpringDataNotificationDeliveryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<NotificationDelivery> findByTaskId(UUID taskId) {
        return repository.findById(taskId).map(this::toDomain);
    }

    @Override
    public NotificationDelivery save(NotificationDelivery delivery) {
        return toDomain(repository.saveAndFlush(toEntity(delivery)));
    }

    private NotificationDeliveryJpaEntity toEntity(NotificationDelivery source) {
        var target = new NotificationDeliveryJpaEntity();
        target.taskId = source.taskId();
        target.causationId = source.causationId();
        target.businessReferenceId = source.businessReferenceId();
        target.type = source.type();
        target.recipientKind = source.recipient().kind();
        target.recipientReferenceId = source.recipient().referenceId();
        target.templateKey = source.templateKey();
        target.status = source.status();
        target.receivedAt = source.receivedAt();
        target.deliveredAt = source.deliveredAt();
        return target;
    }

    private NotificationDelivery toDomain(NotificationDeliveryJpaEntity source) {
        return NotificationDelivery.rehydrate(
                source.taskId, source.causationId, source.businessReferenceId,
                source.type, new Recipient(source.recipientKind, source.recipientReferenceId),
                source.templateKey, source.status, source.receivedAt, source.deliveredAt);
    }
}
