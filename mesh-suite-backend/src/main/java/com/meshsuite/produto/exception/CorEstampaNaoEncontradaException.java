package com.meshsuite.produto.exception;

public class CorEstampaNaoEncontradaException extends RuntimeException {
    public CorEstampaNaoEncontradaException() {
        super("Cor/Estampa não encontrada");
    }
}
