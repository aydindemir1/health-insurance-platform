package com.aydindemir.health.claims.infrastructure.messaging;

import com.aydindemir.health.claims.application.port.out.ProcessedMessageRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
class JpaProcessedMessageRepository implements ProcessedMessageRepository {
    private final SpringDataProcessedMessageRepository repository;

    JpaProcessedMessageRepository(SpringDataProcessedMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(UUID messageId) {
        return repository.existsById(messageId);
    }

    @Override
    public void markProcessed(UUID messageId, String consumerName, Instant processedAt) {
        repository.save(new ProcessedMessageJpaEntity(messageId, consumerName, processedAt));
    }
}
