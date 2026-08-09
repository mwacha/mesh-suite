package com.meshsuite.produto.exception;

public class TabelaPrecoNaoEncontradaException extends RuntimeException {
    public TabelaPrecoNaoEncontradaException() {
        super("Tabela de preço não encontrada");
    }
}
