package com.meshsuite.auth;

public class AuthException extends RuntimeException {
    public AuthException() {
        super("E-mail ou senha inválidos");
    }
}
