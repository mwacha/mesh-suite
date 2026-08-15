package com.meshsuite.salesorder.dto;

import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesOrderResponse(
        UUID id,
        Integer number,
        UUID customerId,
        String customerName,
        UUID salespersonId,
        String salespersonName,
        LocalDate orderDate,
        LocalDate deliveryDate,
        SalesOrderStatus status,
        BigDecimal discount,
        BigDecimal subtotal,
        BigDecimal total,
        List<SalesOrderItemResponse> items) {
}
