package com.aydindemir.health.policy.infrastructure.cache;

import com.aydindemir.health.policy.application.command.EvaluateCoverageCommand;
import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;
import com.aydindemir.health.policy.application.port.out.CoverageEvaluationCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Component
public class RedisCoverageEvaluationCache implements CoverageEvaluationCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCoverageEvaluationCache.class);
    private static final String KEY_PREFIX = "policy:coverage:v1:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisCoverageEvaluationCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.cache.coverage.ttl:30s}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<CoverageEvaluationResult> find(EvaluateCoverageCommand command) {
        try {
            String value = redis.opsForValue().get(key(command));
            return value == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(value, CoverageEvaluationResult.class));
        } catch (Exception exception) {
            LOGGER.warn("Coverage cache read failed; continuing with the policy database", exception);
            return Optional.empty();
        }
    }

    @Override
    public void store(EvaluateCoverageCommand command, CoverageEvaluationResult result) {
        try {
            String entryKey = key(command);
            String indexKey = policyIndexKey(command.policyNumber());
            redis.opsForValue().set(entryKey, objectMapper.writeValueAsString(result), ttl);
            redis.opsForSet().add(indexKey, entryKey);
            redis.expire(indexKey, ttl);
        } catch (Exception exception) {
            LOGGER.warn("Coverage cache write failed; the evaluated result remains valid", exception);
        }
    }

    @Override
    public void evictPolicy(String policyNumber) {
        try {
            String indexKey = policyIndexKey(policyNumber);
            var entryKeys = redis.opsForSet().members(indexKey);
            if (entryKeys != null && !entryKeys.isEmpty()) {
                redis.delete(entryKeys);
            }
            redis.delete(indexKey);
        } catch (Exception exception) {
            LOGGER.warn("Coverage cache eviction failed; entries will expire by TTL", exception);
        }
    }

    private String key(EvaluateCoverageCommand command) {
        String canonical = String.join("|",
                normalize(command.policyNumber()),
                command.memberId().toString(),
                normalize(command.serviceCode()),
                command.requestedAmount().stripTrailingZeros().toPlainString(),
                command.currency().getCurrencyCode(),
                command.serviceDate().toString());
        return KEY_PREFIX + policyHash(command.policyNumber()) + ":evaluation:" + sha256(canonical);
    }

    private String policyIndexKey(String policyNumber) {
        return KEY_PREFIX + policyHash(policyNumber) + ":keys";
    }

    private String policyHash(String policyNumber) {
        return sha256(normalize(policyNumber));
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
