package com.meshsuite.product.exception;

import com.meshsuite.product.controller.ProductController;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductExceptionHandler {

    private static final String SKU_UNIQUE_INDEX = "idx_produto_tenant_sku";
    private static final String KIT_COMPONENT_FK = "product_kit_item_component_product_id_fkey";

    // Three distinct constraints can surface here now that products have types:
    // the SKU uniqueness index (pre-existing), the product_kit_item FK that
    // blocks deleting a product still referenced as a kit component (Kit
    // strategy), and anything else (e.g. a value too long for a column) --
    // each needs its own message, not one generic "duplicado".
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String constraintName = e.getCause() instanceof ConstraintViolationException cve
                ? cve.getConstraintName()
                : null;
        if (SKU_UNIQUE_INDEX.equals(constraintName)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Já existe um produto cadastrado com este SKU"));
        }
        if (KIT_COMPONENT_FK.equals(constraintName)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Este produto não pode ser excluído: está sendo usado como componente em um kit."));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensagem", "Verifique os dados informados."));
    }
}
