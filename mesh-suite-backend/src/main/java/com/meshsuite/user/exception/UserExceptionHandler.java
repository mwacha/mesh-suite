package com.meshsuite.user.exception;

import com.meshsuite.user.controller.UserController;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserController.class)
public class UserExceptionHandler {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "app_user_tenant_id_email_key";

    // Any other integrity violation (e.g. a value too long for a column)
    // must NOT be reported as an e-mail duplicate -- that mislabels an
    // unrelated failure.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve
                && EMAIL_UNIQUE_CONSTRAINT.equals(cve.getConstraintName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Já existe um usuário cadastrado com este e-mail"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensagem", "Verifique os dados informados."));
    }
}
