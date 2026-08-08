package com.meshsuite.produto;

public class TabelaPrecoNaoEncontradaException extends RuntimeException {
    public TabelaPrecoNaoEncontradaException() {
        super("Tabela de preço não encontrada");
    }
}
