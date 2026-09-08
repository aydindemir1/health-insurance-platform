package com.aydindemir.health.authorization.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.slf4j.MDC;
import com.aydindemir.health.authorization.infrastructure.observability.CorrelationIdFilter;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class PolicyServiceClientConfiguration {
    @Bean
    RestClient policyServiceRestClient(
            @Value("${clients.policy-service.base-url}") String baseUrl,
            @Value("${clients.policy-service.connect-timeout}") Duration connectTimeout,
            @Value("${clients.policy-service.read-timeout}") Duration readTimeout) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlationId != null) request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
                    return execution.execute(request, body);
                })
                .build();
    }
}
