package com.meshsuite.produto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "produto_kit_item")
@Getter
@Setter
public class ProdutoKitItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    // The PRODUCT_KIT-typed Produto this item belongs to.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_kit_id", nullable = false)
    private Produto produtoKit;

    // The PRODUCT-typed component. Kits may only be composed of plain
    // products (enforced in ProdutoKitService), never other kits or
    // variation parents/children.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;
}
