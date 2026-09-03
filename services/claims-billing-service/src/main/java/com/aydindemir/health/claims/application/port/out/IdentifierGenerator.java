package com.aydindemir.health.claims.application.port.out;

import java.util.UUID;

public interface IdentifierGenerator {
    UUID generate();
}
