package com.aydindemir.health.claims.domain.exception;

public class InvalidInvoiceStateException extends RuntimeException {
    public InvalidInvoiceStateException(String message) {
        super(message);
    }
}
