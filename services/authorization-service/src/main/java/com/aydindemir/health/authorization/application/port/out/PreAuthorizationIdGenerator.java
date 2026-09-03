package com.aydindemir.health.authorization.application.port.out;

import java.util.UUID;

@FunctionalInterface
public interface PreAuthorizationIdGenerator {
    UUID generate();
}
