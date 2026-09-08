package com.aydindemir.health.claims.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "app.messaging.consumer.retry-delay=100ms",
        "app.messaging.consumer.max-attempts=3"
})
class KafkaPreAuthorizationIntegrationTest {
    private static final String TOPIC = "health.authorization.pre-authorization.v1";
    private static final String DLT = TOPIC + ".DLT";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.1.1");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsOneClaimAndInvoiceWhenTheSameEventIsDeliveredTwice() throws Exception {
        UUID messageId = UUID.randomUUID();
        UUID preAuthorizationId = UUID.randomUUID();
        String payload = approvedPayload(messageId, preAuthorizationId);

        kafkaTemplate.send(TOPIC, preAuthorizationId.toString(), payload).get();
        kafkaTemplate.send(TOPIC, preAuthorizationId.toString(), payload).get();

        await(() -> count("processed_messages", "message_id", messageId) == 1, Duration.ofSeconds(15));
        assertThat(count("claims", "pre_authorization_id", preAuthorizationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from invoices invoice join claims claim on claim.id=invoice.claim_id " +
                        "where claim.pre_authorization_id=?", Integer.class, preAuthorizationId))
                .isEqualTo(1);
    }

    @Test
    void publishesPoisonMessageToDeadLetterTopicAfterRetries() throws Exception {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-verification-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of(DLT));
            kafkaTemplate.send(TOPIC, "poison", "not-json").get();

            String deadLetterValue = null;
            Instant deadline = Instant.now().plusSeconds(15);
            while (Instant.now().isBefore(deadline) && deadLetterValue == null) {
                for (var record : consumer.poll(Duration.ofMillis(250))) {
                    deadLetterValue = record.value();
                }
            }
            assertThat(deadLetterValue).isEqualTo("not-json");
        }
    }

    private int count(String table, String column, UUID id) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where " + column + "=?",
                Integer.class,
                id);
    }

    private static void await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Condition was not satisfied within " + timeout);
    }

    private static String approvedPayload(UUID messageId, UUID preAuthorizationId) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"PreAuthorizationApproved",
                  "eventVersion":1,
                  "preAuthorizationId":"%s",
                  "memberId":"20000000-0000-0000-0000-000000000001",
                  "providerId":"30000000-0000-0000-0000-000000000001",
                  "policyNumber":"POL-KAFKA-TEST",
                  "serviceCode":"IMG-MRI",
                  "requestedAmount":1250.00,
                  "currency":"TRY",
                  "decision":"APPROVED",
                  "reason":"Coverage verified",
                  "occurredAt":"2026-09-08T12:00:00Z"
                }
                """.formatted(messageId, preAuthorizationId);
    }
}
