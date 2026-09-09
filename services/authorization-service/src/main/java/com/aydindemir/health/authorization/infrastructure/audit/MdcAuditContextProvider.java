package com.aydindemir.health.authorization.infrastructure.audit;

import com.aydindemir.health.authorization.application.port.out.AuditContextProvider;
import com.aydindemir.health.authorization.infrastructure.observability.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
class MdcAuditContextProvider implements AuditContextProvider {
    private static final String UNAVAILABLE = "not-available";

    @Override
    public String correlationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return correlationId == null || correlationId.isBlank() ? UNAVAILABLE : correlationId;
    }
}
