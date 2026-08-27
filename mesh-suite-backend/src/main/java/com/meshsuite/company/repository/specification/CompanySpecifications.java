package com.meshsuite.company.repository.specification;

import com.meshsuite.company.domain.Company;
import org.springframework.data.jpa.domain.Specification;

public final class CompanySpecifications {

    private CompanySpecifications() {
    }

    public static Specification<Company> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("legalName")), term),
                cb.like(cb.lower(root.get("tradeName")), term),
                cb.like(cb.lower(root.get("cnpj")), term));
    }

    public static Specification<Company> withActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    public static Specification<Company> withState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("state"), state);
    }

    public static Specification<Company> withCity(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("city"), city);
    }
}
