package com.aydindemir.health.claims.infrastructure.messaging;

import com.aydindemir.health.claims.application.command.HandleApprovedPreAuthorizationCommand;
import com.aydindemir.health.claims.application.port.in.HandleApprovedPreAuthorizationUseCase;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Currency;

@Component
@ConditionalOnProperty(name = "app.messaging.consumer.enabled", matchIfMissing = true)
class PreAuthorizationDecisionListener {
    private final ObjectMapper objectMapper;
    private final HandleApprovedPreAuthorizationUseCase useCase;

    PreAuthorizationDecisionListener(
            ObjectMapper objectMapper,
            HandleApprovedPreAuthorizationUseCase useCase) {
        this.objectMapper = objectMapper;
        this.useCase = useCase;
    }

    @KafkaListener(
            topics = "health.authorization.pre-authorization.v1",
            groupId = "claims-billing-pre-authorization-v1")
    void consume(String payload) {
        PreAuthorizationDecisionMessage message;
        try {
            message = objectMapper.readValue(payload, PreAuthorizationDecisionMessage.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid pre-authorization event payload", exception);
        }
        if (message.eventVersion() != 1) {
            throw new IllegalArgumentException("Unsupported pre-authorization event version: " + message.eventVersion());
        }
        if (!"APPROVED".equals(message.decision())) {
            return;
        }
        useCase.handle(new HandleApprovedPreAuthorizationCommand(
                message.eventId(), message.preAuthorizationId(), message.memberId(),
                message.providerId(), message.policyNumber(), message.serviceCode(),
                message.requestedAmount(), Currency.getInstance(message.currency()),
                message.occurredAt()));
    }
}
