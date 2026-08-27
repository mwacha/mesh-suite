package com.meshsuite.paymentmethod.exception;

public class PaymentMethodNotFoundException extends RuntimeException {
    public PaymentMethodNotFoundException() {
        super("Forma de recebimento não encontrada");
    }
}
