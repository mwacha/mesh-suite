package com.meshsuite.sale.repository;

import com.meshsuite.sale.domain.SaleCounter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleCounterRepository extends JpaRepository<SaleCounter, UUID> {
}
