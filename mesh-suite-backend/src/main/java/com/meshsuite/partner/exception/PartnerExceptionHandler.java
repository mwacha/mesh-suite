package com.meshsuite.partner.exception;

import com.meshsuite.partner.controller.PartnerController;
import com.meshsuite.partner.service.PartnerService;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PartnerController.class)
public class PartnerExceptionHandler {

    private static final String DOCUMENT_UNIQUE_CONSTRAINT = "idx_partner_tenant_document";

    // Fallback for a race condition slipping past PartnerService's pre-check
    // (two concurrent requests for the same new document) -- the DB's
    // UNIQUE(tenant_id, document) constraint is the actual source of truth.
    // Any other integrity violation (e.g. a value too long for a column)
    // must NOT be reported as a document duplicate: that mislabels an
    // unrelated failure and sends the user chasing a partner that was never
    // created.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve
                && DOCUMENT_UNIQUE_CONSTRAINT.equals(cve.getConstraintName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Já existe um parceiro cadastrado com este documento"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensagem", "Verifique os dados informados."));
    }
}
