package com.meshsuite.parceiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ParceiroRepository extends JpaRepository<Parceiro, UUID>, JpaSpecificationExecutor<Parceiro> {
    boolean existsByDocumento(String documento);
    boolean existsByDocumentoAndIdNot(String documento, UUID id);
    long countByStatus(StatusParceiro status);
}
