package com.meshsuite.purchaseorder.repository.specification;

import com.meshsuite.purchaseorder.domain.PurchaseOrder;
import com.meshsuite.purchaseorder.domain.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.domain.Specification;

public final class PurchaseOrderSpecifications {

    private PurchaseOrderSpecifications() {
    }

    public static Specification<PurchaseOrder> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        Integer number = tryParseInt(search.trim());
        return (root, query, cb) -> {
            var byText = cb.or(
                    cb.like(cb.lower(root.get("supplier").get("nomeFantasia")), term),
                    cb.like(cb.lower(root.get("buyer").get("name")), term));
            if (number != null) {
                return cb.or(byText, cb.equal(root.get("number"), number));
            }
            return byText;
        };
    }

    public static Specification<PurchaseOrder> withStatus(PurchaseOrderStatus status) {
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
