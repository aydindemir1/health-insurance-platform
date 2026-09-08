package com.aydindemir.health.claims.application.port.out;

import com.aydindemir.health.claims.application.event.ClaimSearchProjection;

public interface ClaimSearchProjectionOutbox {
    void append(ClaimSearchProjection projection);
}
