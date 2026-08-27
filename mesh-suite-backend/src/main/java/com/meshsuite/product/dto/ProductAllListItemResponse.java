package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductAllListItemResponse(
        UUID id,
        String name,
        String sku,
        String brandName,
        ProductType type,
        BigDecimal salePrice,
        BigDecimal stockQuantity,
        ProductStatus status,
        List<VariationChildSummaryResponse> children) {
}
