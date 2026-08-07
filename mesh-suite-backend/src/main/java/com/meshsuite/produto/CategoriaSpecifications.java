package com.meshsuite.produto;

import org.springframework.data.jpa.domain.Specification;

public final class CategoriaSpecifications {

    private CategoriaSpecifications() {
    }

    public static Specification<Categoria> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nome")), termo);
    }

    public static Specification<Categoria> comAtivo(Boolean ativo) {
        if (ativo == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
    }
}
