package com.meshsuite.parceiro;

public class DocumentoDuplicadoException extends RuntimeException {
    public DocumentoDuplicadoException() {
        super("Já existe um parceiro cadastrado com este documento");
    }
}
