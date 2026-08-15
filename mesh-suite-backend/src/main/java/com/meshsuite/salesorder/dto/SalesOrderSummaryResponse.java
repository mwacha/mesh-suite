package com.meshsuite.salesorder.dto;

import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalesOrderSummaryResponse(
        UUID id,
        Integer number,
        String customerName,
        String salespersonName,
        LocalDate orderDate,
        BigDecimal total,
        SalesOrderStatus status) {
}
