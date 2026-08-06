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

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<Map<String, String>> handlePermissionDenied(PermissionDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.user.UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(
            com.meshsuite.user.UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.user.EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailAlreadyExists(
            com.meshsuite.user.EmailAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.user.UserValidationException.class)
    public ResponseEntity<Map<String, String>> handleUserValidation(
            com.meshsuite.user.UserValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
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

    @ExceptionHandler(com.meshsuite.produto.ProdutoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleProdutoNaoEncontrado(
            com.meshsuite.produto.ProdutoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.SkuDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleSkuDuplicado(
            com.meshsuite.produto.SkuDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pedido.PedidoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handlePedidoNaoEncontrado(
            com.meshsuite.pedido.PedidoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pedido.PedidoValidacaoException.class)
    public ResponseEntity<Map<String, String>> handlePedidoValidacao(
            com.meshsuite.pedido.PedidoValidacaoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.purchaseorder.PurchaseOrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePurchaseOrderNotFound(
            com.meshsuite.purchaseorder.PurchaseOrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.purchaseorder.PurchaseOrderValidationException.class)
    public ResponseEntity<Map<String, String>> handlePurchaseOrderValidation(
            com.meshsuite.purchaseorder.PurchaseOrderValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.stock.StockValidationException.class)
    public ResponseEntity<Map<String, String>> handleStockValidation(
            com.meshsuite.stock.StockValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.payable.AccountsPayableNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAccountsPayableNotFound(
            com.meshsuite.payable.AccountsPayableNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.payable.AccountsPayableValidationException.class)
    public ResponseEntity<Map<String, String>> handleAccountsPayableValidation(
            com.meshsuite.payable.AccountsPayableValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
}
