package com.meshsuite.purchaseinvoice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseInvoiceItemResponse(
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalValue,
        BigDecimal icmsAmount,
        BigDecimal ipiAmount,
        BigDecimal pisAmount,
        BigDecimal cofinsAmount) {
}
