package com.meshsuite.product.exception;

import com.meshsuite.product.controller.ProductController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductExceptionHandler {

    private static final String SKU_UNIQUE_INDEX = "idx_produto_tenant_sku";

    // Two distinct constraints can surface here now that products have types:
    // the SKU uniqueness index (pre-existing) and the product_kit_item FK that
    // blocks deleting a product still referenced as a kit component (new in the
    // Kit strategy) -- each needs its own message, not one generic "SKU duplicado".
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String cause = String.valueOf(e.getMostSpecificCause().getMessage());
        if (cause.contains(SKU_UNIQUE_INDEX)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Já existe um produto cadastrado com este SKU"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Operação viola uma regra de integridade dos dados"));
    }
}
