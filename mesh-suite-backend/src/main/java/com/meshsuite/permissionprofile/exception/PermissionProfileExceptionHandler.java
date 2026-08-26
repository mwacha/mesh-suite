package com.meshsuite.permissionprofile.exception;

import com.meshsuite.permissionprofile.controller.PermissionProfileController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PermissionProfileController.class)
public class PermissionProfileExceptionHandler {

    // Fallback for a race condition slipping past PermissionProfileService's
    // pre-check (two concurrent requests for the same new name, or the
    // default-seed race documented in PermissionProfileService) -- the DB's
    // UNIQUE(tenant_id, name) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe um perfil de permissão cadastrado com este nome"));
    }
}
