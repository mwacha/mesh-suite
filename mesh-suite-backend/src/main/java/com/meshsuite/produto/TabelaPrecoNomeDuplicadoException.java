package com.meshsuite.produto;

public class TabelaPrecoNomeDuplicadoException extends RuntimeException {
    public TabelaPrecoNomeDuplicadoException() {
        super("Já existe uma tabela de preço cadastrada com este nome");
    }
}
