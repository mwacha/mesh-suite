package com.meshsuite.sale.repository.specification;

import com.meshsuite.sale.domain.Sale;
import org.springframework.data.jpa.domain.Specification;

public final class SaleSpecifications {

    private SaleSpecifications() {
    }

    public static Specification<Sale> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        Integer number = tryParseInt(search.trim());
        return (root, query, cb) -> {
            var byCustomer = cb.like(cb.lower(root.get("customer").get("tradeName")), term);
            if (number != null) {
                return cb.or(byCustomer, cb.equal(root.get("number"), number));
            }
            return byCustomer;
        };
    }

    private static Integer tryParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
