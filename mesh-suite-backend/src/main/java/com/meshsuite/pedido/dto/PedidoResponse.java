package com.meshsuite.pedido.dto;

import com.meshsuite.pedido.domain.enums.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PedidoResponse(
        UUID id,
        Integer numero,
        UUID clienteId,
        String clienteNome,
        UUID vendedorId,
        String vendedorNome,
        LocalDate dataPedido,
        LocalDate dataEntrega,
        StatusPedido status,
        BigDecimal desconto,
        BigDecimal subtotal,
        BigDecimal total,
        List<ItemPedidoResponse> itens) {
}
