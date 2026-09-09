package com.aydindemir.health.policy.infrastructure.audit;

import com.aydindemir.health.policy.application.port.out.AuditContextProvider;
import com.aydindemir.health.policy.infrastructure.observability.CorrelationIdFilter;
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
