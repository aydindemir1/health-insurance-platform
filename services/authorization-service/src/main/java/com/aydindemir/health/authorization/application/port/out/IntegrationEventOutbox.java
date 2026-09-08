package com.aydindemir.health.authorization.application.port.out;

import com.aydindemir.health.authorization.application.event.PreAuthorizationDecisionEvent;

public interface IntegrationEventOutbox {
    void append(PreAuthorizationDecisionEvent event);
}
