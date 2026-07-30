package com.meshsuite.parceiro;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = ParceiroController.class)
public class ParceiroExceptionHandler {

    // Fallback for a race condition slipping past ParceiroService's pre-check
    // (two concurrent requests for the same new documento) -- the DB's
    // UNIQUE(tenant_id, documento) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe um parceiro cadastrado com este documento"));
    }
}
