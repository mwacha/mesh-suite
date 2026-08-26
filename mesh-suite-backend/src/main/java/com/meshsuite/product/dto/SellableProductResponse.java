package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Row shape for the order-entry item picker. Carries the fields the picker renders or
 * filters on beyond the plain listing -- {@code type} for the Simples/Kit/Variação badge,
 * and {@code size}/{@code colorwayName} for the Tamanho and Cor filters.
 */
public record SellableProductResponse(
        UUID id,
        String name,
        String sku,
        ProductType type,
        BigDecimal salePrice,
        BigDecimal stockQuantity,
        ProductStatus status,
        String size,
        String colorwayName) {
}
