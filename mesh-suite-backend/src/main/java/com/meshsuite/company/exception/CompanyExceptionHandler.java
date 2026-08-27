package com.meshsuite.company.exception;

import com.meshsuite.company.controller.CompanyController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CompanyController.class)
public class CompanyExceptionHandler {

    // Fallback for a race condition slipping past CompanyService's pre-check
    // (two concurrent requests for the same new CNPJ) -- the DB's
    // UNIQUE(cnpj) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe uma empresa cadastrada com este CNPJ"));
    }
}
