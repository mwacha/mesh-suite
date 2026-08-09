package com.meshsuite.produto.exception;

public class CategoriaNomeDuplicadoException extends RuntimeException {
    public CategoriaNomeDuplicadoException() {
        super("Já existe uma categoria cadastrada com este nome");
    }
}
