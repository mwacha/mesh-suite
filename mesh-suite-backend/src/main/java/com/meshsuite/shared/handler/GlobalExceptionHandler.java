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

    @ExceptionHandler(com.meshsuite.product.exception.ProductNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleProductNotFound(
            com.meshsuite.product.exception.ProductNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.product.exception.DuplicateSkuException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateSku(
            com.meshsuite.product.exception.DuplicateSkuException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.product.exception.ProductValidationException.class)
    public ResponseEntity<Map<String, String>> handleProductValidation(
            com.meshsuite.product.exception.ProductValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.salesorder.exception.SalesOrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSalesOrderNotFound(
            com.meshsuite.salesorder.exception.SalesOrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.salesorder.exception.SalesOrderValidationException.class)
    public ResponseEntity<Map<String, String>> handleSalesOrderValidation(
            com.meshsuite.salesorder.exception.SalesOrderValidationException e) {
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

    @ExceptionHandler(com.meshsuite.category.exception.CategoryNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCategoryNotFound(
            com.meshsuite.category.exception.CategoryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.category.exception.DuplicateCategoryNameException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateCategoryName(
            com.meshsuite.category.exception.DuplicateCategoryNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.category.exception.CategoryInUseException.class)
    public ResponseEntity<Map<String, String>> handleCategoryInUse(
            com.meshsuite.category.exception.CategoryInUseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.category.exception.CategoryValidationException.class)
    public ResponseEntity<Map<String, String>> handleCategoryValidation(
            com.meshsuite.category.exception.CategoryValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.colorway.exception.ColorwayNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleColorwayNotFound(
            com.meshsuite.colorway.exception.ColorwayNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.colorway.exception.DuplicateColorwayNameException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateColorwayName(
            com.meshsuite.colorway.exception.DuplicateColorwayNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.colorway.exception.ColorwayInUseException.class)
    public ResponseEntity<Map<String, String>> handleColorwayInUse(
            com.meshsuite.colorway.exception.ColorwayInUseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pricetable.exception.PriceTableNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePriceTableNotFound(
            com.meshsuite.pricetable.exception.PriceTableNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pricetable.exception.DuplicatePriceTableNameException.class)
    public ResponseEntity<Map<String, String>> handleDuplicatePriceTableName(
            com.meshsuite.pricetable.exception.DuplicatePriceTableNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.pricetable.exception.PriceTableValidationException.class)
    public ResponseEntity<Map<String, String>> handlePriceTableValidation(
            com.meshsuite.pricetable.exception.PriceTableValidationException e) {
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

    @ExceptionHandler(com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePurchaseInvoiceNotFound(
            com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceValidationException.class)
    public ResponseEntity<Map<String, String>> handlePurchaseInvoiceValidation(
            com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.paymentmethod.exception.PaymentMethodNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePaymentMethodNotFound(
            com.meshsuite.paymentmethod.exception.PaymentMethodNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.paymentmethod.exception.DuplicatePaymentMethodDescriptionException.class)
    public ResponseEntity<Map<String, String>> handleDuplicatePaymentMethodDescription(
            com.meshsuite.paymentmethod.exception.DuplicatePaymentMethodDescriptionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.paymentmethod.exception.PaymentMethodValidationException.class)
    public ResponseEntity<Map<String, String>> handlePaymentMethodValidation(
            com.meshsuite.paymentmethod.exception.PaymentMethodValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.permissionprofile.exception.PermissionProfileNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePermissionProfileNotFound(
            com.meshsuite.permissionprofile.exception.PermissionProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.permissionprofile.exception.DuplicatePermissionProfileNameException.class)
    public ResponseEntity<Map<String, String>> handleDuplicatePermissionProfileName(
            com.meshsuite.permissionprofile.exception.DuplicatePermissionProfileNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.permissionprofile.exception.PermissionProfileValidationException.class)
    public ResponseEntity<Map<String, String>> handlePermissionProfileValidation(
            com.meshsuite.permissionprofile.exception.PermissionProfileValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.brand.exception.BrandNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleBrandNotFound(
            com.meshsuite.brand.exception.BrandNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.brand.exception.DuplicateBrandNameException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateBrandName(
            com.meshsuite.brand.exception.DuplicateBrandNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.brand.exception.BrandInUseException.class)
    public ResponseEntity<Map<String, String>> handleBrandInUse(
            com.meshsuite.brand.exception.BrandInUseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.company.exception.CompanyNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCompanyNotFound(
            com.meshsuite.company.exception.CompanyNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.company.exception.DuplicateCnpjException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateCnpj(
            com.meshsuite.company.exception.DuplicateCnpjException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(com.meshsuite.company.exception.CompanyIsLastForTenantException.class)
    public ResponseEntity<Map<String, String>> handleCompanyIsLastForTenant(
            com.meshsuite.company.exception.CompanyIsLastForTenantException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensagem", e.getMessage()));
    }
}
