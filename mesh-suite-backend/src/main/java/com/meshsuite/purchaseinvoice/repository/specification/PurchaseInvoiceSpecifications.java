package com.meshsuite.purchaseinvoice.repository.specification;

import com.meshsuite.purchaseinvoice.domain.PurchaseInvoice;
import org.springframework.data.jpa.domain.Specification;

public final class PurchaseInvoiceSpecifications {

    private PurchaseInvoiceSpecifications() {
    }

    public static Specification<PurchaseInvoice> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        Integer number = tryParseInt(search.trim());
        return (root, query, cb) -> {
            var byText = cb.or(
                    cb.like(cb.lower(root.get("supplier").get("tradeName")), term),
                    cb.like(cb.lower(root.get("invoiceNumber")), term));
            if (number != null) {
                return cb.or(byText, cb.equal(root.get("number"), number));
            }
            return byText;
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
