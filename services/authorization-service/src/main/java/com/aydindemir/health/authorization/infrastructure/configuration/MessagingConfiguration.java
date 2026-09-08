package com.aydindemir.health.authorization.infrastructure.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class MessagingConfiguration {
    @Bean
    NewTopic preAuthorizationEventsTopic() {
        return TopicBuilder.name("health.authorization.pre-authorization.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic preAuthorizationEventsDeadLetterTopic() {
        return TopicBuilder.name("health.authorization.pre-authorization.v1.DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
