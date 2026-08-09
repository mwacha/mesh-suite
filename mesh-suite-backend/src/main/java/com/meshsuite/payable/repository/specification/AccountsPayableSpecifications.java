package com.meshsuite.payable.repository.specification;

import com.meshsuite.payable.domain.AccountsPayable;
import com.meshsuite.payable.domain.enums.AccountsPayableStatus;
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
