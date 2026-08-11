package com.meshsuite.partner.exception;

public class DuplicateDocumentException extends RuntimeException {
    public DuplicateDocumentException() {
        super("Já existe um parceiro cadastrado com este documento");
    }
}
