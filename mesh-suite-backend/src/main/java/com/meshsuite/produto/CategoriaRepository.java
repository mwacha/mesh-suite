package com.meshsuite.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID>, JpaSpecificationExecutor<Categoria> {
    boolean existsByNome(String nome);
    boolean existsByNomeAndIdNot(String nome, UUID id);
}
