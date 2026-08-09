package com.meshsuite.payable.repository;

import com.meshsuite.payable.domain.AccountsPayable;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AccountsPayableRepository extends JpaRepository<AccountsPayable, UUID>, JpaSpecificationExecutor<AccountsPayable> {
}
