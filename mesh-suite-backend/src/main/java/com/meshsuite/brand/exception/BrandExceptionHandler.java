package com.meshsuite.brand.exception;

import com.meshsuite.brand.controller.BrandController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BrandController.class)
public class BrandExceptionHandler {

    // Fallback for a race condition slipping past BrandService's pre-check
    // (two concurrent requests for the same new name) -- the DB's
    // UNIQUE(tenant_id, name) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe uma marca cadastrada com este nome"));
    }
}
