package com.meshsuite.company.exception;

public class DuplicateCnpjException extends RuntimeException {
    public DuplicateCnpjException() {
        super("Já existe uma empresa cadastrada com este CNPJ");
    }
}
