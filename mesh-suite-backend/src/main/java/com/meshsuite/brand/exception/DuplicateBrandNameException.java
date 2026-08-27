package com.meshsuite.brand.exception;

public class DuplicateBrandNameException extends RuntimeException {
    public DuplicateBrandNameException() {
        super("Já existe uma marca cadastrada com este nome");
    }
}
