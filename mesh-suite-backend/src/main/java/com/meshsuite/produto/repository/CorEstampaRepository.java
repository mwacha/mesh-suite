package com.meshsuite.produto.repository;

import com.meshsuite.produto.domain.CorEstampa;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CorEstampaRepository extends JpaRepository<CorEstampa, UUID>, JpaSpecificationExecutor<CorEstampa> {
    boolean existsByNome(String nome);
    boolean existsByNomeAndIdNot(String nome, UUID id);
}
