package com.meshsuite.pricetable.exception;

public class PriceTableNotFoundException extends RuntimeException {
    public PriceTableNotFoundException() {
        super("Tabela de preço não encontrada");
    }
}
