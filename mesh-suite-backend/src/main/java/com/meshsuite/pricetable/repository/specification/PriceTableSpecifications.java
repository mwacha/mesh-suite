package com.meshsuite.pricetable.repository.specification;

import com.meshsuite.pricetable.domain.PriceTable;
import org.springframework.data.jpa.domain.Specification;

public final class PriceTableSpecifications {

    private PriceTableSpecifications() {
    }

    public static Specification<PriceTable> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), termo);
    }

    public static Specification<PriceTable> comAtivo(Boolean ativo) {
        if (ativo == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), ativo);
    }
}
