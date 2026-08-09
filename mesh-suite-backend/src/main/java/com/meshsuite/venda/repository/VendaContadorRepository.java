package com.meshsuite.venda.repository;

import com.meshsuite.venda.domain.VendaContador;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendaContadorRepository extends JpaRepository<VendaContador, UUID> {
}
