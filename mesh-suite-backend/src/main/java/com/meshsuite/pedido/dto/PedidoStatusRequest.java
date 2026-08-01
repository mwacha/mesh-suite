package com.meshsuite.pedido.dto;

import com.meshsuite.pedido.StatusPedido;
import jakarta.validation.constraints.NotNull;

public record PedidoStatusRequest(@NotNull StatusPedido status) {
}
