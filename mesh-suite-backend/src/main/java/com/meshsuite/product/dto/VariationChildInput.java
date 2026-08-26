package com.meshsuite.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record VariationChildInput(
        UUID id,
        @NotBlank String sku,
        String barcode,
        @NotNull @DecimalMin(value = "0.01") BigDecimal salePrice,
        BigDecimal costPrice,
        BigDecimal stockQuantity,
        BigDecimal minStock,
        BigDecimal maxStock,
        String size,
        UUID colorwayId,
        BigDecimal saleMultiple) {
}
