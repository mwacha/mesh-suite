package com.meshsuite.venda.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VendaSummaryResponse(
        UUID id,
        Integer numero,
        String clienteNome,
        LocalDate dataEmissao,
        BigDecimal total) {
}
