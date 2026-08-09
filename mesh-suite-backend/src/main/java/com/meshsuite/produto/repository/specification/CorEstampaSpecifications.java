package com.meshsuite.produto.repository.specification;

import com.meshsuite.produto.domain.CorEstampa;
import org.springframework.data.jpa.domain.Specification;

public final class CorEstampaSpecifications {

    private CorEstampaSpecifications() {
    }

    public static Specification<CorEstampa> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nome")), termo);
    }

    public static Specification<CorEstampa> comAtivo(Boolean ativo) {
        if (ativo == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
    }
}
