package com.aydindemir.health.authorization.infrastructure.messaging;

import com.aydindemir.health.authorization.application.event.PreAuthorizationNotificationTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JpaNotificationTaskOutboxTest {
    @Test
    void mapsTheApplicationTaskToTheDedicatedOutboxEntity() {
        var repository = mock(SpringDataNotificationTaskOutboxRepository.class);
        var outbox = new JpaNotificationTaskOutbox(repository);
        var task = new PreAuthorizationNotificationTask(
                UUID.randomUUID(), UUID.randomUUID(), 1,
                PreAuthorizationNotificationTask.NotificationType.PRE_AUTHORIZATION_APPROVED,
                UUID.randomUUID(), PreAuthorizationNotificationTask.RecipientKind.PROVIDER,
                UUID.randomUUID(), "pre-authorization-approved-v1",
                Instant.parse("2026-09-08T18:00:00Z"));

        outbox.append(task);

        var captor = ArgumentCaptor.forClass(NotificationTaskOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.taskId).isEqualTo(task.taskId());
        assertThat(saved.causationId).isEqualTo(task.causationId());
        assertThat(saved.businessReferenceId).isEqualTo(task.businessReferenceId());
        assertThat(saved.recipientReferenceId).isEqualTo(task.recipientReferenceId());
        assertThat(saved.templateKey).isEqualTo(task.templateKey());
        assertThat(saved.publishAttempts).isZero();
        assertThat(saved.publishedAt).isNull();
    }
}
