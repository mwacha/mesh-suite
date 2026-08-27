package com.meshsuite.brand.repository.specification;

import com.meshsuite.brand.domain.Brand;
import org.springframework.data.jpa.domain.Specification;

public final class BrandSpecifications {

    private BrandSpecifications() {
    }

    public static Specification<Brand> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), term);
    }

    public static Specification<Brand> withActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
