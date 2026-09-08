package com.aydindemir.health.claims.infrastructure.messaging;

import com.aydindemir.health.claims.application.event.ClaimSearchProjection;
import com.aydindemir.health.claims.application.port.out.ClaimSearchProjectionOutbox;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
class JpaClaimSearchProjectionOutbox implements ClaimSearchProjectionOutbox {
    static final String EVENT_TYPE = "ClaimSearchProjectionUpdated";
    static final int EVENT_VERSION = 1;
    private final SpringDataClaimSearchOutboxRepository repository;
    private final ObjectMapper objectMapper;

    JpaClaimSearchProjectionOutbox(SpringDataClaimSearchOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(ClaimSearchProjection projection) {
        UUID eventId = UUID.randomUUID();
        var event = new ClaimSearchProjectionMessage(
                eventId, EVENT_TYPE, EVENT_VERSION, projection.claimId(), projection.invoiceId(),
                projection.preAuthorizationId(), projection.memberId(), projection.providerId(),
                projection.policyNumber(), projection.serviceCode(), projection.claimedAmount(),
                projection.approvedAmount(), projection.payableAmount(), projection.paidAmount(),
                projection.currency(), projection.claimStatus(), projection.invoiceStatus(),
                projection.invoiceNumber(), projection.occurredAt());
        try {
            repository.save(new ClaimSearchOutboxJpaEntity(
                    eventId, projection.claimId(), EVENT_TYPE, EVENT_VERSION,
                    projection.occurredAt(), objectMapper.writeValueAsString(event)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize claim search projection", exception);
        }
    }
}
