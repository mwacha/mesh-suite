package com.meshsuite.venda.domain;

import com.meshsuite.produto.domain.Produto;
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
@Table(name = "item_venda")
@Getter
@Setter
public class ItemVenda {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "valor_icms", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorIcms;

    @Column(name = "valor_ipi", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorIpi;

    @Column(name = "valor_pis", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorPis;

    @Column(name = "valor_cofins", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorCofins;
}
