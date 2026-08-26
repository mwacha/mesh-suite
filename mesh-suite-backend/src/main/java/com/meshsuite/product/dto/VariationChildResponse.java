package com.meshsuite.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VariationChildResponse(
        UUID id,
        String sku,
        String barcode,
        BigDecimal salePrice,
        BigDecimal costPrice,
        BigDecimal stockQuantity,
        BigDecimal minStock,
        BigDecimal maxStock,
        String size,
        UUID colorwayId,
        String colorwayName,
        BigDecimal saleMultiple) {
}
