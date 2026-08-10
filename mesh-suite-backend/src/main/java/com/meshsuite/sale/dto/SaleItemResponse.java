package com.meshsuite.sale.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        BigDecimal icmsAmount,
        BigDecimal ipiAmount,
        BigDecimal pisAmount,
        BigDecimal cofinsAmount) {
}
