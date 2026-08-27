package com.meshsuite.permissionprofile.exception;

public class PermissionProfileNotFoundException extends RuntimeException {
    public PermissionProfileNotFoundException() {
        super("Perfil de permissão não encontrado");
    }
}
