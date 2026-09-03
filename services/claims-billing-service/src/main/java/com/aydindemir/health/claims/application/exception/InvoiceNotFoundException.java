package com.aydindemir.health.claims.application.exception;

import java.util.UUID;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(UUID id) {
        super("Invoice was not found: " + id);
    }
}
