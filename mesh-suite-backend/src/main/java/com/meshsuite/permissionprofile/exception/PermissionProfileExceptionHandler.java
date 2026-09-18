package com.meshsuite.permissionprofile.exception;

import com.meshsuite.permissionprofile.controller.PermissionProfileController;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PermissionProfileController.class)
public class PermissionProfileExceptionHandler {

    private static final String NAME_UNIQUE_INDEX = "idx_permission_profile_tenant_name";

    // Fallback for a race condition slipping past PermissionProfileService's
    // pre-check (two concurrent requests for the same new name, or the
    // default-seed race documented in PermissionProfileService) -- the DB's
    // UNIQUE(tenant_id, name) constraint is the actual source of truth. Any
    // other integrity violation (e.g. a value too long for a column, or the
    // separate UNIQUE(tenant_id, code) constraint) must NOT be reported as a
    // name duplicate -- that mislabels an unrelated failure.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve
                && NAME_UNIQUE_INDEX.equals(cve.getConstraintName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Já existe um perfil de permissão cadastrado com este nome"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensagem", "Verifique os dados informados."));
    }
}
