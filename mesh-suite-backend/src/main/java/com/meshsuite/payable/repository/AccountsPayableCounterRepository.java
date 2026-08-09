package com.meshsuite.payable.repository;

import com.meshsuite.payable.domain.AccountsPayableCounter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountsPayableCounterRepository extends JpaRepository<AccountsPayableCounter, UUID> {
}
