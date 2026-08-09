package com.meshsuite.venda.exception;

public class VendaNaoEncontradaException extends RuntimeException {
    public VendaNaoEncontradaException() {
        super("Venda não encontrada");
    }
}
