package com.meshsuite.salesorder.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesOrderItemResponse(
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount) {
}
