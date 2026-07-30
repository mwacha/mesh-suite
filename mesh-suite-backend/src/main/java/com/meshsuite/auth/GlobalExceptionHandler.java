package com.meshsuite.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("mensagem", "Muitas tentativas, tente novamente em instantes"));
    }

    @ExceptionHandler(com.meshsuite.parceiro.ParceiroNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleParceiroNaoEncontrado(
            com.meshsuite.parceiro.ParceiroNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.parceiro.DocumentoDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleDocumentoDuplicado(
            com.meshsuite.parceiro.DocumentoDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.parceiro.ParceiroValidacaoException.class)
    public ResponseEntity<Map<String, String>> handleParceiroValidacao(
            com.meshsuite.parceiro.ParceiroValidacaoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    // Fallback for a race condition slipping past ParceiroService's pre-check
    // (two concurrent requests for the same new documento) -- the DB's
    // UNIQUE(tenant_id, documento) constraint is the actual source of truth.
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Já existe um parceiro cadastrado com este documento"));
    }
}
