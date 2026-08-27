package com.meshsuite.paymentmethod.dto;

import com.meshsuite.paymentmethod.domain.enums.PaymentMethodType;
import java.util.List;
import java.util.UUID;

public record PaymentMethodSummaryResponse(
        UUID id,
        String description,
        PaymentMethodType type,
        Boolean active,
        Integer maxInstallments,
        Integer installmentsCount,
        // Dias de vencimento de cada parcela, em ordem -- a listagem usa para
        // montar a coluna "Parcelamento" (ex.: 30/60/90).
        List<Integer> installmentDays) {
}
