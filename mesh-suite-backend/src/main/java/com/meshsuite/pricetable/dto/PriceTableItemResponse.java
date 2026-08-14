package com.meshsuite.pricetable.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceTableItemResponse(
        UUID productId,
        String productName,
        String productSku,
        BigDecimal registeredPrice,
        BigDecimal tablePrice,
        BigDecimal commissionPercentage) {
}
