package com.meshsuite.product.repository.specification;

import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> hasSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), term),
                cb.like(cb.lower(root.get("sku")), term));
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Product> hasType(ProductType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }
}
