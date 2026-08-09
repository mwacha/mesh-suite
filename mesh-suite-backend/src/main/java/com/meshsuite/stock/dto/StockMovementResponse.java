package com.meshsuite.stock.dto;

import com.meshsuite.stock.domain.enums.StockMovementOrigin;
import com.meshsuite.stock.domain.enums.StockMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID productId,
        String productName,
        StockMovementType type,
        BigDecimal quantity,
        StockMovementOrigin origin,
        UUID referenceId,
        BigDecimal balanceAfter,
        UUID userId,
        String userName,
        String note,
        Instant createdAt) {
}
