package com.meshsuite.partner.exception;

import com.meshsuite.partner.controller.PartnerController;
import com.meshsuite.partner.service.PartnerService;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PartnerController.class)
public class PartnerExceptionHandler {

    // Fallback for a race condition slipping past PartnerService's pre-check
    // (two concurrent requests for the same new document) -- the DB's
    // UNIQUE(tenant_id, document) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe um parceiro cadastrado com este documento"));
    }
}
