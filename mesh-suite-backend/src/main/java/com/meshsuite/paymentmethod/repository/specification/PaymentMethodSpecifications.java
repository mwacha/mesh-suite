package com.meshsuite.paymentmethod.repository.specification;

import com.meshsuite.paymentmethod.domain.PaymentMethod;
import org.springframework.data.jpa.domain.Specification;

public final class PaymentMethodSpecifications {

    private PaymentMethodSpecifications() {
    }

    public static Specification<PaymentMethod> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), term);
    }

    public static Specification<PaymentMethod> withActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
