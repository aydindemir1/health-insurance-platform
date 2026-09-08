package com.aydindemir.health.notification.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.messaging.rabbit-listener.enabled", havingValue = "true")
class NotificationRabbitTopology {
    @Bean
    DirectExchange notificationExchange() {
        return new DirectExchange(NotificationRabbitContract.EXCHANGE, true, false);
    }

    @Bean
    Queue notificationDeliveryQueue() {
        return QueueBuilder.durable(NotificationRabbitContract.DELIVERY_QUEUE)
                .deadLetterExchange(NotificationRabbitContract.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(NotificationRabbitContract.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding notificationDeliveryBinding(Queue notificationDeliveryQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationDeliveryQueue)
                .to(notificationExchange)
                .with(NotificationRabbitContract.ROUTING_KEY);
    }

    @Bean
    DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(NotificationRabbitContract.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(NotificationRabbitContract.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue,
            DirectExchange notificationDeadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(NotificationRabbitContract.DEAD_LETTER_ROUTING_KEY);
    }
}
