package com.meshsuite.produto.repository;

import com.meshsuite.produto.domain.TabelaPreco;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TabelaPrecoRepository extends JpaRepository<TabelaPreco, UUID>, JpaSpecificationExecutor<TabelaPreco> {
    boolean existsByNome(String nome);
    boolean existsByNomeAndIdNot(String nome, UUID id);
}
