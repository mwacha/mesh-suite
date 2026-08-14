package com.meshsuite.salesorder.exception;

public class SalesOrderNotFoundException extends RuntimeException {
    public SalesOrderNotFoundException() {
        super("Pedido não encontrado");
    }
}
