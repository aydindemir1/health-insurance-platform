package com.aydindemir.health.authorization.infrastructure.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitNotificationTopologyTest {
    private final RabbitNotificationTopology topology = new RabbitNotificationTopology();

    @Test
    void declaresDurableDeliveryQueueWithDeadLetterRoute() {
        var queue = topology.notificationDeliveryQueue();

        assertThat(queue.getName()).isEqualTo(RabbitNotificationTopology.DELIVERY_QUEUE);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitNotificationTopology.DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitNotificationTopology.DEAD_LETTER_ROUTING_KEY);
    }

    @Test
    void bindsDeliveryAndDeadLetterQueuesToTheirDirectExchanges() {
        var exchange = topology.notificationExchange();
        var queue = topology.notificationDeliveryQueue();
        var deliveryBinding = topology.notificationDeliveryBinding(queue, exchange);
        var deadLetterExchange = topology.notificationDeadLetterExchange();
        var deadLetterQueue = topology.notificationDeadLetterQueue();
        var deadLetterBinding = topology.notificationDeadLetterBinding(deadLetterQueue, deadLetterExchange);

        assertThat(exchange.isDurable()).isTrue();
        assertThat(deliveryBinding.getRoutingKey()).isEqualTo(RabbitNotificationTopology.ROUTING_KEY);
        assertThat(deliveryBinding.getDestination()).isEqualTo(RabbitNotificationTopology.DELIVERY_QUEUE);
        assertThat(deadLetterExchange.isDurable()).isTrue();
        assertThat(deadLetterQueue.isDurable()).isTrue();
        assertThat(deadLetterBinding.getRoutingKey())
                .isEqualTo(RabbitNotificationTopology.DEAD_LETTER_ROUTING_KEY);
        assertThat(deadLetterBinding.getDestination())
                .isEqualTo(RabbitNotificationTopology.DEAD_LETTER_QUEUE);
    }
}
