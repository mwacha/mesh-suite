package com.meshsuite.auth.exception;

public class AuthException extends RuntimeException {
    public AuthException() {
        super("E-mail ou senha inválidos");
    }
}
