package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.domain.enums.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

public record KitProductRequest(
        @NotBlank String name,
        @NotBlank String sku,
        String barcode,
        MeasurementUnit measurementUnit,
        ProductStatus status,
        String description,
        @NotEmpty List<@Valid KitItemInput> items,
        BigDecimal saleMultiple) {
}
