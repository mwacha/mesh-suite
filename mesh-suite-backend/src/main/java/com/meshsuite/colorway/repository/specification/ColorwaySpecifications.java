package com.meshsuite.colorway.repository.specification;

import com.meshsuite.colorway.domain.Colorway;
import org.springframework.data.jpa.domain.Specification;

public final class ColorwaySpecifications {

    private ColorwaySpecifications() {
    }

    public static Specification<Colorway> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), term);
    }

    public static Specification<Colorway> withActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
