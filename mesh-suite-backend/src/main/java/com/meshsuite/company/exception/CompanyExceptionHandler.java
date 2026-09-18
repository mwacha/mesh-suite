package com.meshsuite.company.exception;

import com.meshsuite.company.controller.CompanyController;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CompanyController.class)
public class CompanyExceptionHandler {

    private static final String CNPJ_UNIQUE_CONSTRAINT = "company_cnpj_key";

    // Fallback for a race condition slipping past CompanyService's pre-check
    // (two concurrent requests for the same new CNPJ) -- the DB's
    // UNIQUE(cnpj) constraint is the actual source of truth. Any other
    // integrity violation (e.g. a value too long for a column) must NOT be
    // reported as a CNPJ duplicate: that mislabels an unrelated failure and
    // sends the user chasing a company that was never created.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve
                && CNPJ_UNIQUE_CONSTRAINT.equals(cve.getConstraintName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Já existe uma empresa cadastrada com este CNPJ"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensagem", "Verifique os dados informados."));
    }
}
