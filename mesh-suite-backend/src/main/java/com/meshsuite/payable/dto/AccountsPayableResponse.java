package com.meshsuite.payable.dto;

import com.meshsuite.payable.domain.enums.AccountsPayableStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountsPayableResponse(
        UUID id,
        Integer number,
        Integer installmentNumber,
        Integer totalInstallments,
        UUID supplierId,
        String supplierName,
        BigDecimal amount,
        LocalDate issueDate,
        LocalDate dueDate,
        LocalDate paymentDate,
        AccountsPayableStatus status,
        UUID referenceId,
        Instant createdAt) {
}
