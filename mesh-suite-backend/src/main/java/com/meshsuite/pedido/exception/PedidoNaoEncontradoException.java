package com.meshsuite.pedido.exception;

import com.meshsuite.pedido.domain.Pedido;

public class PedidoNaoEncontradoException extends RuntimeException {
    public PedidoNaoEncontradoException() {
        super("Pedido não encontrado");
    }
}
