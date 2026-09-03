package com.aydindemir.health.claims.application.exception;

public class InvoiceNumberConflictException extends RuntimeException {
    public InvoiceNumberConflictException(String invoiceNumber) {
        super("Invoice number already exists: " + invoiceNumber);
    }

    public InvoiceNumberConflictException(String invoiceNumber, Throwable cause) {
        super("Invoice number already exists: " + invoiceNumber, cause);
    }
}
