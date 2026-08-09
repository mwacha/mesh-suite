package com.meshsuite.pedido.repository;

import com.meshsuite.pedido.domain.PedidoContador;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoContadorRepository extends JpaRepository<PedidoContador, UUID> {
}
