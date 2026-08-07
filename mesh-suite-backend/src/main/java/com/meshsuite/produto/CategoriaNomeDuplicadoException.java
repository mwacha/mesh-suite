package com.meshsuite.produto;

public class CategoriaNomeDuplicadoException extends RuntimeException {
    public CategoriaNomeDuplicadoException() {
        super("Já existe uma categoria cadastrada com este nome");
    }
}
