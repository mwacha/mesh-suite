package com.meshsuite.pedido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PedidoContadorRepository extends JpaRepository<PedidoContador, UUID> {
}
