package com.meshsuite.category.repository.specification;

import com.meshsuite.category.domain.Category;
import org.springframework.data.jpa.domain.Specification;

public final class CategorySpecifications {

    private CategorySpecifications() {
    }

    public static Specification<Category> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), term);
    }

    public static Specification<Category> withActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    public static Specification<Category> onlyRoot(Boolean root) {
        if (root == null || !root) {
            return null;
        }
        return (r, query, cb) -> cb.isNull(r.get("parent"));
    }
}
