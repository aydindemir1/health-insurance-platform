package com.aydindemir.health.notification.infrastructure.configuration;

import com.aydindemir.health.notification.application.exception.TransientNotificationDeliveryException;
import com.aydindemir.health.notification.application.port.in.DeliverNotificationUseCase;
import com.aydindemir.health.notification.application.port.out.NotificationDeliveryRepository;
import com.aydindemir.health.notification.application.port.out.NotificationSender;
import com.aydindemir.health.notification.domain.model.NotificationDelivery;
import com.aydindemir.health.notification.infrastructure.messaging.NotificationTaskMessage;
import com.aydindemir.health.notification.infrastructure.messaging.RabbitNotificationListener;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.retry.support.RetryTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationConfigurationTest {
    @Test
    void commitsApplicationTransactionBeforeListenerAcknowledgesWithoutDatabaseOrBroker() throws Exception {
        var deliveries = mock(NotificationDeliveryRepository.class);
        var sender = mock(NotificationSender.class);
        var transactions = mock(PlatformTransactionManager.class);
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(deliveries.findByTaskId(any())).thenReturn(Optional.empty());
        when(deliveries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NotificationDeliveryRepository.class, () -> deliveries);
            context.registerBean(NotificationSender.class, () -> sender);
            context.registerBean(PlatformTransactionManager.class, () -> transactions);
            context.register(TransactionTestConfiguration.class, ApplicationConfiguration.class);
            context.refresh();

            DeliverNotificationUseCase useCase = context.getBean(DeliverNotificationUseCase.class);
            assertThat(AopUtils.isAopProxy(useCase)).isTrue();
            var channel = mock(Channel.class);
            var listener = new RabbitNotificationListener(useCase, RetryTemplate.defaultInstance());
            listener.receive(new NotificationTaskMessage(
                    UUID.randomUUID(), UUID.randomUUID(), 1,
                    "PRE_AUTHORIZATION_APPROVED", UUID.randomUUID(), "PROVIDER",
                    UUID.randomUUID(), "pre-authorization-approved-v1",
                    Instant.parse("2026-09-08T18:00:00Z")), channel, 73L);

            var order = inOrder(transactions, channel);
            order.verify(transactions).commit(status);
            order.verify(channel).basicAck(73L, false);
        }

        verify(sender).send(any());
        verify(deliveries, org.mockito.Mockito.times(2)).save(any(NotificationDelivery.class));
    }

    @Test
    void opensASeparateTransactionForEachTransientRetryBeforeAcknowledging() throws Exception {
        var deliveries = mock(NotificationDeliveryRepository.class);
        var sender = mock(NotificationSender.class);
        var transactions = mock(PlatformTransactionManager.class);
        TransactionStatus failedAttempt = new SimpleTransactionStatus();
        TransactionStatus successfulAttempt = new SimpleTransactionStatus();
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(failedAttempt, successfulAttempt);
        when(deliveries.findByTaskId(any())).thenReturn(Optional.empty());
        when(deliveries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new TransientNotificationDeliveryException("provider unavailable"))
                .doNothing()
                .when(sender).send(any());

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NotificationDeliveryRepository.class, () -> deliveries);
            context.registerBean(NotificationSender.class, () -> sender);
            context.registerBean(PlatformTransactionManager.class, () -> transactions);
            context.register(TransactionTestConfiguration.class, ApplicationConfiguration.class);
            context.refresh();

            DeliverNotificationUseCase useCase = context.getBean(DeliverNotificationUseCase.class);
            var retry = RetryTemplate.builder()
                    .maxAttempts(2)
                    .noBackoff()
                    .retryOn(TransientNotificationDeliveryException.class)
                    .build();
            var channel = mock(Channel.class);
            new RabbitNotificationListener(useCase, retry).receive(validTask(), channel, 74L);

            var order = inOrder(transactions, channel);
            order.verify(transactions).rollback(failedAttempt);
            order.verify(transactions).commit(successfulAttempt);
            order.verify(channel).basicAck(74L, false);
        }
    }

    private NotificationTaskMessage validTask() {
        return new NotificationTaskMessage(
                UUID.randomUUID(), UUID.randomUUID(), 1,
                "PRE_AUTHORIZATION_APPROVED", UUID.randomUUID(), "PROVIDER",
                UUID.randomUUID(), "pre-authorization-approved-v1",
                Instant.parse("2026-09-08T18:00:00Z"));
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionTestConfiguration {
    }
}
