package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.domain.enums.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VariationParentRequest(
        @NotBlank String name,
        @NotBlank String sku,
        String brand,
        UUID categoryId,
        @NotNull BigDecimal salePrice,
        ProductStatus status,
        String description,
        MeasurementUnit measurementUnit,
        @NotEmpty List<@Valid VariationChildInput> children,
        BigDecimal saleMultiple,
        List<@Valid VariationAxisInput> variationAxes) {
}
