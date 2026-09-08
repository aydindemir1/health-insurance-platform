package com.aydindemir.health.authorization.infrastructure.messaging;

import com.aydindemir.health.authorization.application.event.PreAuthorizationDecisionEvent;
import com.aydindemir.health.authorization.application.port.out.IntegrationEventOutbox;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
class JpaIntegrationEventOutbox implements IntegrationEventOutbox {
    private final SpringDataOutboxRepository repository;
    private final ObjectMapper objectMapper;

    JpaIntegrationEventOutbox(SpringDataOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(PreAuthorizationDecisionEvent event) {
        try {
            repository.save(new OutboxMessageJpaEntity(
                    event.eventId(), event.preAuthorizationId(), event.eventType(), event.eventVersion(),
                    event.occurredAt(), objectMapper.writeValueAsString(event)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize integration event", exception);
        }
    }
}
