package com.meshsuite.colorway.exception;

import com.meshsuite.colorway.controller.ColorwayController;
import com.meshsuite.colorway.service.ColorwayService;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ColorwayController.class)
public class ColorwayExceptionHandler {

    private static final String NAME_UNIQUE_INDEX = "idx_colorway_tenant_name";

    // Fallback for a race condition slipping past ColorwayService's pre-check
    // (two concurrent requests for the same new name) -- the DB's
    // UNIQUE(tenant_id, name) constraint is the actual source of truth. Any
    // other integrity violation (e.g. a value too long for a column) must
    // NOT be reported as a name duplicate -- that mislabels an unrelated
    // failure.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve
                && NAME_UNIQUE_INDEX.equals(cve.getConstraintName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Já existe uma cor/estampa cadastrada com este nome"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensagem", "Verifique os dados informados."));
    }
}
