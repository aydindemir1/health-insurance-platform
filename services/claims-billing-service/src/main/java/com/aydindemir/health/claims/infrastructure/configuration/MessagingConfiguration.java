package com.aydindemir.health.claims.infrastructure.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
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

    @Bean
    CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.consumer.retry-delay:1s}") java.time.Duration retryDelay,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.consumer.max-attempts:3}") long maxAttempts) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryDelay.toMillis(), Math.max(0, maxAttempts - 1)));
    }
}
