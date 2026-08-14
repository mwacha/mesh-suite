package com.meshsuite.salesorder.repository.specification;

import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import org.springframework.data.jpa.domain.Specification;

public final class SalesOrderSpecifications {

    private SalesOrderSpecifications() {
    }

    public static Specification<SalesOrder> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        Integer number = tryParseInt(search.trim());
        return (root, query, cb) -> {
            var byText = cb.or(
                    cb.like(cb.lower(root.get("customer").get("tradeName")), term),
                    cb.like(cb.lower(root.get("salesperson").get("name")), term));
            if (number != null) {
                return cb.or(byText, cb.equal(root.get("number"), number));
            }
            return byText;
        };
    }

    public static Specification<SalesOrder> withStatus(SalesOrderStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Integer tryParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
