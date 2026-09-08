package com.aydindemir.health.notification.infrastructure.messaging;

import com.aydindemir.health.notification.application.exception.TransientNotificationDeliveryException;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
class RabbitListenerConfiguration {
    @Bean
    MessageConverter notificationTaskMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RetryTemplate notificationDeliveryRetryTemplate(
            @Value("${app.messaging.rabbit-listener.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.messaging.rabbit-listener.retry.initial-interval:250ms}") java.time.Duration initialInterval,
            @Value("${app.messaging.rabbit-listener.retry.multiplier:2.0}") double multiplier,
            @Value("${app.messaging.rabbit-listener.retry.max-interval:2s}") java.time.Duration maxInterval) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Retry max-attempts must be at least 1");
        }
        return RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .exponentialBackoff(
                        initialInterval.toMillis(), multiplier, maxInterval.toMillis())
                .retryOn(TransientNotificationDeliveryException.class)
                .build();
    }
}
