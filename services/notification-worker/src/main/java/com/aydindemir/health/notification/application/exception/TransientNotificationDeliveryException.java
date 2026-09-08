package com.aydindemir.health.notification.application.exception;

/**
 * Signals a delivery failure that can reasonably succeed when attempted again.
 * Infrastructure adapters should translate timeouts and temporary provider
 * unavailability to this exception without exposing provider details upstream.
 */
public final class TransientNotificationDeliveryException extends RuntimeException {
    public TransientNotificationDeliveryException(String message) {
        super(message);
    }

    public TransientNotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
