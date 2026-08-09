package com.meshsuite.venda.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemVendaResponse(
        UUID produtoId,
        String produtoNome,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        BigDecimal valorIcms,
        BigDecimal valorIpi,
        BigDecimal valorPis,
        BigDecimal valorCofins) {
}
