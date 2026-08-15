package com.meshsuite.salesorder.repository;

import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID>, JpaSpecificationExecutor<SalesOrder> {
    long countByStatus(SalesOrderStatus status);
}
