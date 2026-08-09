package com.meshsuite.purchaseorder.exception;

public class PurchaseOrderNotFoundException extends RuntimeException {
    public PurchaseOrderNotFoundException() {
        super("Ordem de compra não encontrada");
    }
}
