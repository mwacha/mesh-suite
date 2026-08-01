package com.meshsuite.pedido.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemPedidoResponse(
        UUID produtoId,
        String produtoNome,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal) {
}
