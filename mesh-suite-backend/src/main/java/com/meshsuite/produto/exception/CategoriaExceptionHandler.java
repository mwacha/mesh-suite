package com.meshsuite.produto.exception;

import com.meshsuite.produto.controller.CategoriaController;
import com.meshsuite.produto.service.CategoriaService;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CategoriaController.class)
public class CategoriaExceptionHandler {

    // Fallback for a race condition slipping past CategoriaService's pre-check
    // (two concurrent requests for the same new nome) -- the DB's
    // UNIQUE(tenant_id, nome) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe uma categoria cadastrada com este nome"));
    }
}
