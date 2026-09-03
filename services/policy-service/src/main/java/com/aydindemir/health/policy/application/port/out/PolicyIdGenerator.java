package com.aydindemir.health.policy.application.port.out;

import java.util.UUID;

@FunctionalInterface
public interface PolicyIdGenerator {
    UUID generate();
}
