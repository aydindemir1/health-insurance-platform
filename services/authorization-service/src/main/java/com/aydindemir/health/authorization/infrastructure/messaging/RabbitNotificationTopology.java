package com.aydindemir.health.authorization.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.messaging.notification-outbox.enabled", havingValue = "true")
class RabbitNotificationTopology {
    static final String EXCHANGE = "health.notifications";
    static final String ROUTING_KEY = "pre-authorization.decision";
    static final String DELIVERY_QUEUE = "health.notifications.delivery.v1";
    static final String DEAD_LETTER_EXCHANGE = "health.notifications.dlx";
    static final String DEAD_LETTER_QUEUE = "health.notifications.delivery.v1.dlq";
    static final String DEAD_LETTER_ROUTING_KEY = "dead-letter";

    @Bean
    DirectExchange notificationExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue notificationDeliveryQueue() {
        return QueueBuilder.durable(DELIVERY_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding notificationDeliveryBinding(Queue notificationDeliveryQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationDeliveryQueue)
                .to(notificationExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue,
            DirectExchange notificationDeadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}
