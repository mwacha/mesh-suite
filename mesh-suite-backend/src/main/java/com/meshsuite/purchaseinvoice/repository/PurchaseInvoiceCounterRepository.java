package com.meshsuite.purchaseinvoice.repository;

import com.meshsuite.purchaseinvoice.domain.PurchaseInvoiceCounter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseInvoiceCounterRepository extends JpaRepository<PurchaseInvoiceCounter, UUID> {
}
