package com.aydindemir.health.claims.application.query;

import java.util.UUID;

public record AuditRecordSearchCriteria(UUID aggregateId, String action, int page, int size) {
}
