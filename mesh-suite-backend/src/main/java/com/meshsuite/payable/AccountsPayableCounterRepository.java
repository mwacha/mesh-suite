package com.meshsuite.payable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountsPayableCounterRepository extends JpaRepository<AccountsPayableCounter, UUID> {
}
