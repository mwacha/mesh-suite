package com.meshsuite.purchaseinvoice.exception;

public class PurchaseInvoiceNotFoundException extends RuntimeException {
    public PurchaseInvoiceNotFoundException() {
        super("Compra não encontrada");
    }
}
