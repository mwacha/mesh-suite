package com.meshsuite.produto;

public class CategoriaNaoEncontradaException extends RuntimeException {
    public CategoriaNaoEncontradaException() {
        super("Categoria não encontrada");
    }
}
