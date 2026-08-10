package com.meshsuite.sale.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SaleSummaryResponse(
        UUID id,
        Integer number,
        String customerName,
        LocalDate issueDate,
        BigDecimal total) {
}
