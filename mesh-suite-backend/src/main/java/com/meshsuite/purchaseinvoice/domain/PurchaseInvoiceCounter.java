package com.meshsuite.purchaseinvoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "purchase_invoice_counter")
@Getter
@Setter
public class PurchaseInvoiceCounter {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "next_number", nullable = false)
    private Integer nextNumber = 1;
}
