package com.meshsuite.auth;

public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException() {
        super("Você não tem permissão para executar esta ação");
    }
}
