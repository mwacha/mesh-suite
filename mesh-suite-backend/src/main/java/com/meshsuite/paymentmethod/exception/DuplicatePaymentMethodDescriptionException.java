package com.meshsuite.paymentmethod.exception;

public class DuplicatePaymentMethodDescriptionException extends RuntimeException {
    public DuplicatePaymentMethodDescriptionException() {
        super("Já existe uma forma de recebimento cadastrada com este nome");
    }
}
