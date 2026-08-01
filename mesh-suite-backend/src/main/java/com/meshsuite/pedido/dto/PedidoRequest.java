package com.meshsuite.pedido.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PedidoRequest(
        @NotNull UUID clienteId,
        @NotNull UUID vendedorId,
        LocalDate dataPedido,
        LocalDate dataEntrega,
        BigDecimal desconto,
        @NotEmpty List<@Valid ItemPedidoDto> itens) {
}
