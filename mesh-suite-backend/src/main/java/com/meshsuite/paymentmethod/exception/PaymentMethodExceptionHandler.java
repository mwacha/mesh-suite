package com.meshsuite.paymentmethod.exception;

import com.meshsuite.paymentmethod.controller.PaymentMethodController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PaymentMethodController.class)
public class PaymentMethodExceptionHandler {

    // Fallback for a race condition slipping past PaymentMethodService's pre-check
    // (two concurrent requests for the same new description) -- the DB's
    // UNIQUE(tenant_id, description) constraint is the actual source of truth.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe uma forma de recebimento cadastrada com este nome"));
    }
}
