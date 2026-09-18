package com.meshsuite.purchaseorder.exception;

import com.meshsuite.purchaseorder.controller.PurchaseOrderController;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PurchaseOrderController.class)
public class PurchaseOrderExceptionHandler {

    // No single named constraint guards this endpoint -- a real integrity/
    // business-rule violation (unique/FK/check) still means "try again";
    // anything else (e.g. a value too long for a column) is a data problem,
    // not something retrying will fix, so it gets its own message.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Não foi possível salvar a ordem de compra. Tente novamente."));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensagem", "Verifique os dados informados."));
    }
}
