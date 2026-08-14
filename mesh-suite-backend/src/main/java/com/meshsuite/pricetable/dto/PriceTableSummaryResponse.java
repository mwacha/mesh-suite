package com.meshsuite.pricetable.dto;

import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.AdjustmentOperation;
import com.meshsuite.pricetable.domain.enums.AdjustmentValueType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PriceTableSummaryResponse(
        UUID id,
        String name,
        AdjustmentMethod adjustmentMethod,
        AdjustmentOperation adjustmentOperation,
        AdjustmentValueType adjustmentValueType,
        BigDecimal adjustmentValue,
        LocalDate effectiveStartDate,
        LocalDate effectiveEndDate,
        Boolean active) {
}
