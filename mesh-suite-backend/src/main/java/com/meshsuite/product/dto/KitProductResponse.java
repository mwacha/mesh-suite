package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.domain.enums.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record KitProductResponse(
        UUID id,
        String name,
        String sku,
        String barcode,
        MeasurementUnit measurementUnit,
        ProductStatus status,
        String description,
        List<KitItemResponse> items,
        BigDecimal totalPrice,
        BigDecimal saleMultiple) {
}
