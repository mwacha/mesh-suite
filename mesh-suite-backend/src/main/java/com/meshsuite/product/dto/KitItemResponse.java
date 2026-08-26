package com.meshsuite.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record KitItemResponse(
        UUID componentProductId,
        String componentName,
        String componentSku,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice) {
}
