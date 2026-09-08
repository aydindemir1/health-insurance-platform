package com.aydindemir.health.notification.infrastructure.messaging;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitListenerConfiguration {
    @Bean
    MessageConverter notificationTaskMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
