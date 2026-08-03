package com.meshsuite.user;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("Já existe um usuário cadastrado com este e-mail");
    }
}
