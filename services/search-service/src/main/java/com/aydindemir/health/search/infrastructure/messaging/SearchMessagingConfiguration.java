package com.aydindemir.health.search.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.messaging.consumer.enabled", havingValue = "true")
class SearchMessagingConfiguration {
    private static final String AUTHORIZATION_TOPIC = "health.authorization.pre-authorization.v1";
    private static final String CLAIM_TOPIC = "health.claims.search-projection.v1";

    @Bean
    NewTopic searchAuthorizationTopic() {
        return TopicBuilder.name(AUTHORIZATION_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic searchAuthorizationDeadLetterTopic() {
        return TopicBuilder.name(AUTHORIZATION_TOPIC + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic searchClaimTopic() {
        return TopicBuilder.name(CLAIM_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic searchClaimDeadLetterTopic() {
        return TopicBuilder.name(CLAIM_TOPIC + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    CommonErrorHandler searchKafkaErrorHandler(
            KafkaTemplate<String, String> template,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.consumer.retry-delay:1s}") Duration retryDelay,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.consumer.max-attempts:3}") long maxAttempts) {
        var recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryDelay.toMillis(), Math.max(0, maxAttempts - 1)));
    }
}
