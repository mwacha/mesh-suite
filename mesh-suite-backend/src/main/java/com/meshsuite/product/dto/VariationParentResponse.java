package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.domain.enums.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VariationParentResponse(
        UUID id,
        String name,
        String sku,
        String brand,
        UUID categoryId,
        String categoryName,
        BigDecimal salePrice,
        ProductStatus status,
        String description,
        MeasurementUnit measurementUnit,
        List<VariationChildResponse> children,
        BigDecimal saleMultiple,
        List<VariationAxisResponse> variationAxes) {
}
