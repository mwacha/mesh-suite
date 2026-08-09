package com.meshsuite.purchaseorder.repository;

import com.meshsuite.purchaseorder.domain.PurchaseOrderCounter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderCounterRepository extends JpaRepository<PurchaseOrderCounter, UUID> {
}
