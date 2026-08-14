package com.meshsuite.pricetable.dto;

import com.meshsuite.pricetable.domain.enums.Rounding;
import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.ProductSelectionMode;
import com.meshsuite.pricetable.domain.enums.AdjustmentOperation;
import com.meshsuite.pricetable.domain.enums.AdjustmentValueType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PriceTableRequest(
        @NotBlank String name,
        @NotNull ProductSelectionMode productSelectionMode,
        @NotNull AdjustmentMethod adjustmentMethod,
        AdjustmentOperation adjustmentOperation,
        AdjustmentValueType adjustmentValueType,
        BigDecimal adjustmentValue,
        @NotNull Rounding rounding,
        @NotNull LocalDate effectiveStartDate,
        LocalDate effectiveEndDate,
        BigDecimal minSalePrice,
        BigDecimal defaultCommissionPercentage,
        Boolean active,
        @NotNull List<@Valid PriceTableItemInput> items) {
}
