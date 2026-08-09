package com.meshsuite.produto.exception;

public class TabelaPrecoNomeDuplicadoException extends RuntimeException {
    public TabelaPrecoNomeDuplicadoException() {
        super("Já existe uma tabela de preço cadastrada com este nome");
    }
}
