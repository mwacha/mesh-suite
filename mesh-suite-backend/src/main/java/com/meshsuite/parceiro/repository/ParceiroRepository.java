package com.meshsuite.parceiro.repository;

import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.StatusParceiro;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ParceiroRepository extends JpaRepository<Parceiro, UUID>, JpaSpecificationExecutor<Parceiro> {
    boolean existsByDocumento(String documento);
    boolean existsByDocumentoAndIdNot(String documento, UUID id);
    long countByStatus(StatusParceiro status);
    long countByStatusAndPapeisContaining(StatusParceiro status, PapelParceiro papel);
}
