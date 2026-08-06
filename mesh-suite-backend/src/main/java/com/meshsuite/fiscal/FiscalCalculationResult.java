package com.meshsuite.fiscal;

import java.math.BigDecimal;

public record FiscalCalculationResult(
        BigDecimal icmsValue,
        BigDecimal ipiValue,
        BigDecimal pisValue,
        BigDecimal cofinsValue
) {
}
