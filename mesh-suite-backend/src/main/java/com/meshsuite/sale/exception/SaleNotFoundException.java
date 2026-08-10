package com.meshsuite.sale.exception;

public class SaleNotFoundException extends RuntimeException {
    public SaleNotFoundException() {
        super("Venda não encontrada");
    }
}
