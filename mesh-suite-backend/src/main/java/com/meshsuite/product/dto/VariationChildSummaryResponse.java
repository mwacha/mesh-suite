package com.meshsuite.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VariationChildSummaryResponse(
        UUID id,
        String name,
        String sku,
        BigDecimal salePrice,
        BigDecimal stockQuantity) {
}
