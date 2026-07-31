package com.meshsuite.produto;

import org.springframework.data.jpa.domain.Specification;

public final class ProdutoSpecifications {

    private ProdutoSpecifications() {
    }

    public static Specification<Produto> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nome")), termo),
                cb.like(cb.lower(root.get("sku")), termo));
    }

    public static Specification<Produto> comStatus(StatusProduto status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
