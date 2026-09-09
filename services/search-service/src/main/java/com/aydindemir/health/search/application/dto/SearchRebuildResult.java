package com.aydindemir.health.search.application.dto;

import java.util.UUID;

public record SearchRebuildResult(
        UUID runId,
        String candidateIndex,
        String predecessorIndex,
        String status,
        long indexedDocuments) {
}
