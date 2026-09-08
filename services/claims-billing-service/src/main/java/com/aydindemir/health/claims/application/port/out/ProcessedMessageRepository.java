package com.aydindemir.health.claims.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedMessageRepository {
    boolean exists(UUID messageId);
    void markProcessed(UUID messageId, String consumerName, Instant processedAt);
}
