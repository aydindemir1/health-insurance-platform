package com.aydindemir.health.policy.infrastructure.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void propagatesASafeCorrelationIdAndClearsMdcAfterTheRequest() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "policy-request_123");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .isEqualTo("policy-request_123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesAnUnsafeCorrelationIdWithoutReflectingIt() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "member-id\r\ninjected-header");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .matches("[0-9a-f-]{36}")
                .doesNotContain("member-id", "injected-header");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
