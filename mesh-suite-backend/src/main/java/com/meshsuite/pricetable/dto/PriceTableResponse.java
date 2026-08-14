package com.meshsuite.pricetable.dto;

import com.meshsuite.pricetable.domain.enums.Rounding;
import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.ProductSelectionMode;
import com.meshsuite.pricetable.domain.enums.AdjustmentOperation;
import com.meshsuite.pricetable.domain.enums.AdjustmentValueType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PriceTableResponse(
        UUID id,
        String name,
        ProductSelectionMode productSelectionMode,
        AdjustmentMethod adjustmentMethod,
        AdjustmentOperation adjustmentOperation,
        AdjustmentValueType adjustmentValueType,
        BigDecimal adjustmentValue,
        Rounding rounding,
        LocalDate effectiveStartDate,
        LocalDate effectiveEndDate,
        BigDecimal minSalePrice,
        BigDecimal defaultCommissionPercentage,
        Boolean active,
        Instant createdAt,
        List<PriceTableItemResponse> items) {
}
