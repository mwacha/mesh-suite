package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductListItemResponse(
        UUID id,
        String name,
        String sku,
        String brand,
        BigDecimal salePrice,
        BigDecimal stockQuantity,
        ProductStatus status) {
}
