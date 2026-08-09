package com.meshsuite.produto.exception;

public class CorEstampaNomeDuplicadoException extends RuntimeException {
    public CorEstampaNomeDuplicadoException() {
        super("Já existe uma cor/estampa cadastrada com este nome");
    }
}
