package com.meshsuite.venda.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VendaResponse(
        UUID id,
        Integer numero,
        UUID pedidoId,
        Integer pedidoNumero,
        UUID clienteId,
        String clienteNome,
        UUID vendedorId,
        String vendedorNome,
        LocalDate dataEmissao,
        BigDecimal desconto,
        BigDecimal subtotal,
        BigDecimal total,
        BigDecimal valorIcms,
        BigDecimal valorIpi,
        BigDecimal valorPis,
        BigDecimal valorCofins,
        List<ItemVendaResponse> itens) {
}
