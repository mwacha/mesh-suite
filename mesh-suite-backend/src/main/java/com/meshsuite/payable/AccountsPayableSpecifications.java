package com.meshsuite.payable;

import org.springframework.data.jpa.domain.Specification;

public final class AccountsPayableSpecifications {

    private AccountsPayableSpecifications() {
    }

    public static Specification<AccountsPayable> withStatus(AccountsPayableStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
