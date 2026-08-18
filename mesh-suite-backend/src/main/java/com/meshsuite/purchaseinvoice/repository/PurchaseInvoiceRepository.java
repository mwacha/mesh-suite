package com.meshsuite.purchaseinvoice.repository;

import com.meshsuite.purchaseinvoice.domain.PurchaseInvoice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, UUID>, JpaSpecificationExecutor<PurchaseInvoice> {
    Optional<PurchaseInvoice> findBySupplierIdAndInvoiceNumber(UUID supplierId, String invoiceNumber);
}
