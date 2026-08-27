package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank String name,
        @NotBlank String sku,
        String barcode,
        UUID brandId,
        UUID categoryId,
        UUID colorwayId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal salePrice,
        BigDecimal costPrice,
        ProductStatus status,
        String description,
        BigDecimal stockQuantity,
        MeasurementUnit measurementUnit,
        BigDecimal minStock,
        BigDecimal maxStock,
        String size,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        BigDecimal saleMultiple) {
}
