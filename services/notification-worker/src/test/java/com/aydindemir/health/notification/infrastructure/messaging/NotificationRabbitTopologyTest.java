package com.aydindemir.health.notification.infrastructure.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRabbitTopologyTest {
    private final NotificationRabbitTopology topology = new NotificationRabbitTopology();

    @Test
    void declaresProducerCompatibleDurableTopologyAndDeadLetterRoute() {
        var exchange = topology.notificationExchange();
        var queue = topology.notificationDeliveryQueue();
        var binding = topology.notificationDeliveryBinding(queue, exchange);
        var deadLetterExchange = topology.notificationDeadLetterExchange();
        var deadLetterQueue = topology.notificationDeadLetterQueue();
        var deadLetterBinding = topology.notificationDeadLetterBinding(deadLetterQueue, deadLetterExchange);

        assertThat(exchange.getName()).isEqualTo("health.notifications");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(queue.getName()).isEqualTo("health.notifications.delivery.v1");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "health.notifications.dlx")
                .containsEntry("x-dead-letter-routing-key", "dead-letter");
        assertThat(binding.getRoutingKey()).isEqualTo("pre-authorization.decision");
        assertThat(deadLetterExchange.isDurable()).isTrue();
        assertThat(deadLetterQueue.getName()).isEqualTo("health.notifications.delivery.v1.dlq");
        assertThat(deadLetterBinding.getRoutingKey()).isEqualTo("dead-letter");
    }
}
