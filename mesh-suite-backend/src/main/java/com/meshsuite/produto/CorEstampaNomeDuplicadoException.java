package com.meshsuite.produto;

public class CorEstampaNomeDuplicadoException extends RuntimeException {
    public CorEstampaNomeDuplicadoException() {
        super("Já existe uma cor/estampa cadastrada com este nome");
    }
}
