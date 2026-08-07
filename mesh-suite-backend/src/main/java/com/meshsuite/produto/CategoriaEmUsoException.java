package com.meshsuite.produto;

public class CategoriaEmUsoException extends RuntimeException {
    public CategoriaEmUsoException(long quantidadeProdutos) {
        super("Não é possível excluir: " + quantidadeProdutos + " produto(s) usam esta categoria");
    }
}
