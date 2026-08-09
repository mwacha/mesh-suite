package com.meshsuite.produto.exception;

import com.meshsuite.produto.domain.Categoria;

public class CategoriaNaoEncontradaException extends RuntimeException {
    public CategoriaNaoEncontradaException() {
        super("Categoria não encontrada");
    }
}
