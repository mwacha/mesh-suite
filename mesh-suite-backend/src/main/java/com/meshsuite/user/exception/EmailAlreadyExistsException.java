package com.meshsuite.user.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("Já existe um usuário cadastrado com este e-mail");
    }
}
