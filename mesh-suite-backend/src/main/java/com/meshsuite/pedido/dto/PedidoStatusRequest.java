package com.meshsuite.pedido.dto;

import com.meshsuite.pedido.domain.enums.StatusPedido;
import jakarta.validation.constraints.NotNull;

public record PedidoStatusRequest(@NotNull StatusPedido status) {
}
