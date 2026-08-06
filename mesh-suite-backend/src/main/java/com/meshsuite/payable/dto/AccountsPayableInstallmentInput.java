package com.meshsuite.payable.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountsPayableInstallmentInput(BigDecimal amount, LocalDate dueDate) {
}
