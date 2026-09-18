package com.meshsuite.paymentmethod.exception;

import com.meshsuite.paymentmethod.controller.PaymentMethodController;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PaymentMethodController.class)
public class PaymentMethodExceptionHandler {

    private static final String DESCRIPTION_UNIQUE_INDEX = "idx_payment_method_tenant_description";

    // Fallback for a race condition slipping past PaymentMethodService's pre-check
    // (two concurrent requests for the same new description) -- the DB's
    // UNIQUE(tenant_id, description) constraint is the actual source of truth.
    // Any other integrity violation (e.g. a value too long for a column)
    // must NOT be reported as a description duplicate -- that mislabels an
    // unrelated failure.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve
                && DESCRIPTION_UNIQUE_INDEX.equals(cve.getConstraintName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Já existe uma forma de recebimento cadastrada com este nome"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensagem", "Verifique os dados informados."));
    }
}
