package com.meshsuite.pedido.dto;

import com.meshsuite.pedido.domain.enums.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PedidoSummaryResponse(
        UUID id,
        Integer numero,
        String clienteNome,
        String vendedorNome,
        LocalDate dataPedido,
        BigDecimal total,
        StatusPedido status) {
}
