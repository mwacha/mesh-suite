package com.meshsuite.product.domain;

import com.meshsuite.category.domain.Category;
import com.meshsuite.colorway.domain.Colorway;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.MeasurementUnit;
import com.meshsuite.product.domain.enums.ProductType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "product")
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType type = ProductType.PRODUCT;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "barcode", length = 50)
    private String barcode;

    @Column(length = 100)
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "colorway_id")
    private Colorway colorway;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "stock_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_unit", nullable = false, length = 5)
    private MeasurementUnit measurementUnit = MeasurementUnit.UN;

    @Column(name = "sale_multiple", nullable = false, precision = 12, scale = 3)
    private BigDecimal saleMultiple = BigDecimal.ONE;

    @Column(name = "min_stock", precision = 12, scale = 3)
    private BigDecimal minStock;

    @Column(name = "max_stock", precision = 12, scale = 3)
    private BigDecimal maxStock;

    @Column(length = 50)
    private String size;

    @Column(precision = 10, scale = 3)
    private BigDecimal weight;

    @Column(precision = 10, scale = 2)
    private BigDecimal length;

    @Column(precision = 10, scale = 2)
    private BigDecimal width;

    @Column(precision = 10, scale = 2)
    private BigDecimal height;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_registration_id")
    private FiscalRegistration fiscalRegistration;

    // Set only on VARIATION_CHILD rows, pointing back to their VARIATION_PARENT.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_product_id")
    private Product parentProduct;

    // Set only on VARIATION_PARENT rows -- the Tipos de Variação matrix (axis name +
    // its values, e.g. [{"name":"Tamanho","values":["P","M"]}]) that generated this
    // parent's children, serialized as JSON. Stored as plain TEXT (not a native JSONB
    // mapping) to match this codebase's existing conventions -- serialization happens
    // in VariationProductService via Jackson, not at the entity/column level.
    @Column(name = "variation_axes", columnDefinition = "TEXT")
    private String variationAxesJson;

    // Owned only by PRODUCT_KIT rows -- see ProductKitItem. Cleared and rebuilt
    // wholesale on every save, same convention as PriceTable/PurchaseOrder items.
    @OneToMany(mappedBy = "kitProduct", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductKitItem> kitItems = new ArrayList<>();
}
