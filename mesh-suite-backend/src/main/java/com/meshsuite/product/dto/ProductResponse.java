package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String sku,
        String barcode,
        String brand,
        UUID categoryId,
        String categoryName,
        UUID colorwayId,
        String colorwayName,
        BigDecimal salePrice,
        BigDecimal costPrice,
        ProductStatus status,
        String description,
        BigDecimal stockQuantity,
        MeasurementUnit measurementUnit,
        BigDecimal minStock,
        BigDecimal maxStock,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height) {
}
