package com.meshsuite.produto.exception;

import com.meshsuite.produto.domain.Produto;

public class ProdutoNaoEncontradoException extends RuntimeException {
    public ProdutoNaoEncontradoException() {
        super("Produto não encontrado");
    }
}
