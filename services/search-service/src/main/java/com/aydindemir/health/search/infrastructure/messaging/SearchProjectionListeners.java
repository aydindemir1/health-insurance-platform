package com.aydindemir.health.search.infrastructure.messaging;

import com.aydindemir.health.search.application.port.in.IndexSearchRecordUseCase;
import com.aydindemir.health.search.domain.model.RecordType;
import com.aydindemir.health.search.domain.model.SearchRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.messaging.consumer.enabled", havingValue = "true")
class SearchProjectionListeners {
    private final ObjectMapper objectMapper;
    private final IndexSearchRecordUseCase indexer;

    SearchProjectionListeners(ObjectMapper objectMapper, IndexSearchRecordUseCase indexer) {
        this.objectMapper = objectMapper;
        this.indexer = indexer;
    }

    @KafkaListener(topics = "health.authorization.pre-authorization.v1", groupId = "search-pre-authorization-v1")
    void consumePreAuthorization(String payload) {
        var message = read(payload, PreAuthorizationDecisionMessage.class);
        withCorrelation(message.eventId(), () -> {
            requireVersion(message.eventVersion());
            indexer.index(new SearchRecord(
                "PRE_AUTHORIZATION:" + message.preAuthorizationId(), RecordType.PRE_AUTHORIZATION,
                message.preAuthorizationId(), message.preAuthorizationId(), message.memberId(),
                message.providerId(), message.policyNumber(), message.serviceCode(), message.decision(),
                null, null, message.requestedAmount(),
                "APPROVED".equals(message.decision()) ? message.requestedAmount() : null,
                null, message.currency(), message.reason(), message.occurredAt()));
        });
    }

    @KafkaListener(topics = "health.claims.search-projection.v1", groupId = "search-claims-v1")
    void consumeClaim(String payload) {
        var message = read(payload, ClaimSearchProjectionMessage.class);
        withCorrelation(message.eventId(), () -> {
            requireVersion(message.eventVersion());
            indexer.index(new SearchRecord(
                "CLAIM:" + message.claimId(), RecordType.CLAIM, message.claimId(),
                message.preAuthorizationId(), message.memberId(), message.providerId(),
                message.policyNumber(), message.serviceCode(), message.claimStatus(),
                message.invoiceStatus(), message.invoiceNumber(), message.claimedAmount(),
                message.approvedAmount(), message.paidAmount(), message.currency(), null,
                message.occurredAt()));
        });
    }

    private <T> T read(String payload, Class<T> type) {
        try { return objectMapper.readValue(payload, type); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Invalid search projection payload", exception); }
    }

    private void requireVersion(int version) {
        if (version != 1) throw new IllegalArgumentException("Unsupported search projection version: " + version);
    }

    private void withCorrelation(java.util.UUID eventId, Runnable action) {
        MDC.put("correlationId", eventId.toString());
        try { action.run(); } finally { MDC.remove("correlationId"); }
    }
}
