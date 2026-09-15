package com.aydindemir.health.search.infrastructure.messaging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchMessagingConfigurationTest {
    private static final String TOPIC = "health.authorization.pre-authorization.v1";

    @Test
    void recoversPermanentContractFailureOnTheFirstHandlingAttempt() {
        KafkaTemplate<String, String> template = template();
        var handler = handler(template);

        boolean recovered = handler.handleOne(
                new IllegalArgumentException("invalid contract"), consumerRecord(),
                mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(recovered).isTrue();
        verify(template).send(argThat((ProducerRecord<String, String> message) ->
                message.topic().equals(TOPIC + ".DLT")));
    }

    @Test
    void keepsTransientFailureEligibleForBoundedRetry() {
        KafkaTemplate<String, String> template = template();
        var handler = handler(template);

        boolean recovered = handler.handleOne(
                new IllegalStateException("Elasticsearch temporarily unavailable"), consumerRecord(),
                mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(recovered).isFalse();
        verify(template, never()).send(anyProducerRecord());
    }

    private DefaultErrorHandler handler(KafkaTemplate<String, String> template) {
        return (DefaultErrorHandler) new SearchMessagingConfiguration()
                .searchKafkaErrorHandler(template, Duration.ZERO, 3);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private KafkaTemplate<String, String> template() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(anyProducerRecord()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        return template;
    }

    private ProducerRecord<String, String> anyProducerRecord() {
        return org.mockito.ArgumentMatchers.any();
    }

    private ConsumerRecord<String, String> consumerRecord() {
        return new ConsumerRecord<>(TOPIC, 0, 42L, "key", "payload");
    }
}
