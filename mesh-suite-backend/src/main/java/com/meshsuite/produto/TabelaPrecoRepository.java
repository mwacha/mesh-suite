package com.meshsuite.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TabelaPrecoRepository extends JpaRepository<TabelaPreco, UUID>, JpaSpecificationExecutor<TabelaPreco> {
    boolean existsByNome(String nome);
    boolean existsByNomeAndIdNot(String nome, UUID id);
}
