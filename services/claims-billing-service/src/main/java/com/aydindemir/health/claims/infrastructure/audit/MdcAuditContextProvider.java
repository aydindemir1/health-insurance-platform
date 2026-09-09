package com.aydindemir.health.claims.infrastructure.audit;

import com.aydindemir.health.claims.application.port.out.AuditContextProvider;
import com.aydindemir.health.claims.infrastructure.observability.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
class MdcAuditContextProvider implements AuditContextProvider {
    @Override
    public String correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null || value.isBlank() ? "not-available" : value;
    }
}
