package com.meshsuite.product.repository.specification;

import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), termo),
                cb.like(cb.lower(root.get("sku")), termo));
    }

    public static Specification<Product> comStatus(ProductStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
