package com.meshsuite.colorway.exception;

public class DuplicateColorwayNameException extends RuntimeException {
    public DuplicateColorwayNameException() {
        super("Já existe uma cor/estampa cadastrada com este nome");
    }
}
