package com.meshsuite.payable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AccountsPayableRepository extends JpaRepository<AccountsPayable, UUID>, JpaSpecificationExecutor<AccountsPayable> {
}
