package com.meshsuite.paymentmethod.exception;

public class DuplicatePaymentMethodDescriptionException extends RuntimeException {
    public DuplicatePaymentMethodDescriptionException() {
        super("Já existe uma forma de pagamento cadastrada com esta descrição");
    }
}
