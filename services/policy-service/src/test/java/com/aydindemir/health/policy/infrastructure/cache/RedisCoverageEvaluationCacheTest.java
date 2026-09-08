package com.aydindemir.health.policy.infrastructure.cache;

import com.aydindemir.health.policy.application.command.EvaluateCoverageCommand;
import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;
import com.aydindemir.health.policy.application.security.ActorContext;
import com.aydindemir.health.policy.application.security.ApplicationRole;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCoverageEvaluationCacheTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> sets = mock(SetOperations.class);
    private final RedisCoverageEvaluationCache cache = new RedisCoverageEvaluationCache(
            redis, JsonMapper.builder().findAndAddModules().build(), Duration.ofSeconds(30));

    @Test
    void storesAndReadsAnEvaluationWithoutExposingBusinessIdentifiersInTheKey() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        var command = command();
        var result = new CoverageEvaluationResult(
                true, "ELIGIBLE", "Coverage is available", UUID.randomUUID(),
                new BigDecimal("900.00"), "TRY");

        cache.store(command, result);
        var key = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30)));
        assertThat(key.getValue())
                .startsWith("policy:coverage:v1:")
                .doesNotContain("POL-SECRET", command.memberId().toString());

        when(values.get(key.getValue())).thenReturn(
                JsonMapper.builder().findAndAddModules().build().writeValueAsString(result));
        assertThat(cache.find(command)).contains(result);
    }

    @Test
    void treatsRedisFailureAsACacheMiss() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));

        assertThat(cache.find(command())).isEmpty();
    }

    private EvaluateCoverageCommand command() {
        return new EvaluateCoverageCommand(
                new ActorContext("hospital", Set.of(ApplicationRole.HOSPITAL_USER)),
                "POL-SECRET", UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "IMG-MRI", new BigDecimal("100.00"), Currency.getInstance("TRY"),
                LocalDate.parse("2026-09-08"));
    }
}
