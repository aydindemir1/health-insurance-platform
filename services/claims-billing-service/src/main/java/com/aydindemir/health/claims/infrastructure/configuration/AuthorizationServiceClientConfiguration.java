package com.aydindemir.health.claims.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AuthorizationServiceClientConfiguration {
    @Bean
    RestClient authorizationServiceRestClient(
            @Value("${clients.authorization-service.base-url}") String baseUrl,
            @Value("${clients.authorization-service.connect-timeout}") Duration connectTimeout,
            @Value("${clients.authorization-service.read-timeout}") Duration readTimeout) {
        var client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        var requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
