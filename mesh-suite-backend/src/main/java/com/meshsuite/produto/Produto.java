package com.meshsuite.produto;

import com.meshsuite.fiscal.FiscalRegistration;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "produto")
@Getter
@Setter
public class Produto {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "codigo_barras", length = 50)
    private String codigoBarras;

    @Column(length = 100)
    private String marca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(name = "preco_venda", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "preco_custo", precision = 12, scale = 2)
    private BigDecimal precoCusto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusProduto status = StatusProduto.ATIVO;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "quantidade_estoque", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeEstoque = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_medida", nullable = false, length = 5)
    private UnidadeMedida unidadeMedida = UnidadeMedida.UN;

    @Column(name = "estoque_minimo", precision = 12, scale = 3)
    private BigDecimal estoqueMinimo;

    @Column(name = "estoque_maximo", precision = 12, scale = 3)
    private BigDecimal estoqueMaximo;

    @Column(precision = 10, scale = 3)
    private BigDecimal peso;

    @Column(precision = 10, scale = 2)
    private BigDecimal comprimento;

    @Column(precision = 10, scale = 2)
    private BigDecimal largura;

    @Column(precision = 10, scale = 2)
    private BigDecimal altura;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_registration_id")
    private FiscalRegistration fiscalRegistration;
}
