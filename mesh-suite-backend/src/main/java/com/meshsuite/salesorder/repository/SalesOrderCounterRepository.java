package com.meshsuite.salesorder.repository;

import com.meshsuite.salesorder.domain.SalesOrderCounter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderCounterRepository extends JpaRepository<SalesOrderCounter, UUID> {
}
