package com.meshsuite.purchaseinvoice.domain;

import com.meshsuite.partner.domain.Partner;
import com.meshsuite.purchaseorder.domain.PurchaseOrder;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "purchase_invoice")
@Getter
@Setter
public class PurchaseInvoice {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private Integer number;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(nullable = false)
    private String series;

    @Column(nullable = false)
    private String model;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false, unique = true)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Partner supplier;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "icms_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal icmsAmount = BigDecimal.ZERO;

    @Column(name = "ipi_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal ipiAmount = BigDecimal.ZERO;

    @Column(name = "pis_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal pisAmount = BigDecimal.ZERO;

    @Column(name = "cofins_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cofinsAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "purchaseInvoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PurchaseInvoiceItem> items = new ArrayList<>();
}
