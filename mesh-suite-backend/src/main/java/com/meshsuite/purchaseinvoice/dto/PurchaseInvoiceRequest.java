package com.meshsuite.purchaseinvoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record PurchaseInvoiceRequest(
        @NotBlank String invoiceNumber,
        @NotBlank String series,
        @NotBlank String model,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate entryDate,
        @NotEmpty List<@Valid InstallmentInput> installments) {
}
