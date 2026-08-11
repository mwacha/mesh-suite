package com.meshsuite.shared.handler;

import com.meshsuite.auth.exception.AuthException;
import com.meshsuite.auth.exception.PermissionDeniedException;
import com.meshsuite.auth.exception.RateLimitExceededException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(com.meshsuite.user.exception.UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(
            com.meshsuite.user.exception.UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.user.exception.EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailAlreadyExists(
            com.meshsuite.user.exception.EmailAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.user.exception.UserValidationException.class)
    public ResponseEntity<Map<String, String>> handleUserValidation(
            com.meshsuite.user.exception.UserValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("mensagem", "Muitas tentativas, tente novamente em instantes"));
    }

    @ExceptionHandler(com.meshsuite.partner.exception.PartnerNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePartnerNotFound(
            com.meshsuite.partner.exception.PartnerNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.partner.exception.DuplicateDocumentException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateDocument(
            com.meshsuite.partner.exception.DuplicateDocumentException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.partner.exception.PartnerValidationException.class)
    public ResponseEntity<Map<String, String>> handlePartnerValidation(
            com.meshsuite.partner.exception.PartnerValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.ProdutoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleProdutoNaoEncontrado(
            com.meshsuite.produto.exception.ProdutoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.SkuDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleSkuDuplicado(
            com.meshsuite.produto.exception.SkuDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pedido.exception.PedidoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handlePedidoNaoEncontrado(
            com.meshsuite.pedido.exception.PedidoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pedido.exception.PedidoValidacaoException.class)
    public ResponseEntity<Map<String, String>> handlePedidoValidacao(
            com.meshsuite.pedido.exception.PedidoValidacaoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.purchaseorder.exception.PurchaseOrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePurchaseOrderNotFound(
            com.meshsuite.purchaseorder.exception.PurchaseOrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.purchaseorder.exception.PurchaseOrderValidationException.class)
    public ResponseEntity<Map<String, String>> handlePurchaseOrderValidation(
            com.meshsuite.purchaseorder.exception.PurchaseOrderValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.stock.exception.StockValidationException.class)
    public ResponseEntity<Map<String, String>> handleStockValidation(
            com.meshsuite.stock.exception.StockValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.payable.exception.AccountsPayableNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAccountsPayableNotFound(
            com.meshsuite.payable.exception.AccountsPayableNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.payable.exception.AccountsPayableValidationException.class)
    public ResponseEntity<Map<String, String>> handleAccountsPayableValidation(
            com.meshsuite.payable.exception.AccountsPayableValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.CategoriaNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleCategoriaNaoEncontrada(
            com.meshsuite.produto.exception.CategoriaNaoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.CategoriaNomeDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleCategoriaNomeDuplicado(
            com.meshsuite.produto.exception.CategoriaNomeDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.CategoriaEmUsoException.class)
    public ResponseEntity<Map<String, String>> handleCategoriaEmUso(
            com.meshsuite.produto.exception.CategoriaEmUsoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.CorEstampaNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleCorEstampaNaoEncontrada(
            com.meshsuite.produto.exception.CorEstampaNaoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.CorEstampaNomeDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleCorEstampaNomeDuplicado(
            com.meshsuite.produto.exception.CorEstampaNomeDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.CorEstampaEmUsoException.class)
    public ResponseEntity<Map<String, String>> handleCorEstampaEmUso(
            com.meshsuite.produto.exception.CorEstampaEmUsoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.TabelaPrecoNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleTabelaPrecoNaoEncontrada(
            com.meshsuite.produto.exception.TabelaPrecoNaoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.TabelaPrecoNomeDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleTabelaPrecoNomeDuplicado(
            com.meshsuite.produto.exception.TabelaPrecoNomeDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.produto.exception.TabelaPrecoValidationException.class)
    public ResponseEntity<Map<String, String>> handleTabelaPrecoValidation(
            com.meshsuite.produto.exception.TabelaPrecoValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.sale.exception.SaleNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSaleNotFound(
            com.meshsuite.sale.exception.SaleNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.sale.exception.SaleValidationException.class)
    public ResponseEntity<Map<String, String>> handleSaleValidation(
            com.meshsuite.sale.exception.SaleValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
}
