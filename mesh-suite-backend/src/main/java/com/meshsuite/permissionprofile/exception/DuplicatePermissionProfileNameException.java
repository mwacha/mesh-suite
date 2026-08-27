package com.meshsuite.permissionprofile.exception;

public class DuplicatePermissionProfileNameException extends RuntimeException {
    public DuplicatePermissionProfileNameException() {
        super("Já existe um perfil de permissão cadastrado com este nome");
    }
}
