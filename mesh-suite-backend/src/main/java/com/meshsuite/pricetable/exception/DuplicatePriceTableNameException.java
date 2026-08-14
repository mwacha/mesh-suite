package com.meshsuite.pricetable.exception;

public class DuplicatePriceTableNameException extends RuntimeException {
    public DuplicatePriceTableNameException() {
        super("Já existe uma tabela de preço cadastrada com este nome");
    }
}
