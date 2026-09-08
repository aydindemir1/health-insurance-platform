package com.aydindemir.health.policy.infrastructure.cache;

import com.aydindemir.health.policy.application.command.EvaluateCoverageCommand;
import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;
import com.aydindemir.health.policy.application.security.ActorContext;
import com.aydindemir.health.policy.application.security.ApplicationRole;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisCoverageEvaluationCacheIntegrationTest {
    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.2.9-alpine"))
            .withExposedPorts(6379);

    @Test
    void storesReadsAndEvictsCoverageEvaluationsAgainstRedis() {
        var configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        var connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        try {
            var cache = new RedisCoverageEvaluationCache(
                    new StringRedisTemplate(connectionFactory),
                    JsonMapper.builder().findAndAddModules().build(),
                    Duration.ofMinutes(1));
            var command = command();
            var result = new CoverageEvaluationResult(
                    true, "ELIGIBLE", "Coverage is available", UUID.randomUUID(),
                    new BigDecimal("900.00"), "TRY");

            assertThat(cache.find(command)).isEmpty();
            cache.store(command, result);
            assertThat(cache.find(command)).contains(result);

            cache.evictPolicy(command.policyNumber());
            assertThat(cache.find(command)).isEmpty();
        } finally {
            connectionFactory.destroy();
        }
    }

    private EvaluateCoverageCommand command() {
        return new EvaluateCoverageCommand(
                new ActorContext("hospital", Set.of(ApplicationRole.HOSPITAL_USER)),
                "POL-REDIS-100", UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "IMG-MRI", new BigDecimal("100.00"), Currency.getInstance("TRY"),
                LocalDate.parse("2026-09-08"));
    }
}
