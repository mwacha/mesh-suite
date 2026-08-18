package com.meshsuite.purchaseinvoice.domain;

import com.meshsuite.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "purchase_invoice_item")
@Getter
@Setter
public class PurchaseInvoiceItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id", nullable = false)
    private PurchaseInvoice purchaseInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "icms_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal icmsAmount;

    @Column(name = "ipi_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal ipiAmount;

    @Column(name = "pis_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal pisAmount;

    @Column(name = "cofins_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cofinsAmount;
}
