package com.meshsuite.pricetable.domain;

import com.meshsuite.pricetable.domain.enums.Rounding;
import com.meshsuite.pricetable.domain.enums.AdjustmentMethod;
import com.meshsuite.pricetable.domain.enums.ProductSelectionMode;
import com.meshsuite.pricetable.domain.enums.AdjustmentOperation;
import com.meshsuite.pricetable.domain.enums.AdjustmentValueType;
import jakarta.persistence.*;
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
@Table(name = "price_table")
@Getter
@Setter
public class PriceTable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_selection_mode", nullable = false, length = 20)
    private ProductSelectionMode productSelectionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_method", nullable = false, length = 10)
    private AdjustmentMethod adjustmentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_operation", length = 10)
    private AdjustmentOperation adjustmentOperation;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_value_type", length = 12)
    private AdjustmentValueType adjustmentValueType;

    @Column(name = "adjustment_value", precision = 12, scale = 2)
    private BigDecimal adjustmentValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rounding rounding;

    @Column(name = "effective_start_date", nullable = false)
    private LocalDate effectiveStartDate;

    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    @Column(name = "min_sale_price", precision = 12, scale = 2)
    private BigDecimal minSalePrice;

    @Column(name = "default_commission_percentage", precision = 5, scale = 2)
    private BigDecimal defaultCommissionPercentage;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "priceTable", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PriceTableItem> items = new ArrayList<>();
}
