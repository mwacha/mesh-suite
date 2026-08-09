package com.meshsuite.produto.domain;

import com.meshsuite.produto.domain.enums.Arredondamento;
import com.meshsuite.produto.domain.enums.MetodoAjuste;
import com.meshsuite.produto.domain.enums.ModoSelecaoProdutos;
import com.meshsuite.produto.domain.enums.OperacaoAjuste;
import com.meshsuite.produto.domain.enums.TipoValorAjuste;
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
@Table(name = "tabela_preco")
@Getter
@Setter
public class TabelaPreco {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_selecao_produtos", nullable = false, length = 20)
    private ModoSelecaoProdutos modoSelecaoProdutos;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_ajuste", nullable = false, length = 10)
    private MetodoAjuste metodoAjuste;

    @Enumerated(EnumType.STRING)
    @Column(name = "operacao_ajuste", length = 10)
    private OperacaoAjuste operacaoAjuste;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_valor_ajuste", length = 12)
    private TipoValorAjuste tipoValorAjuste;

    @Column(name = "valor_ajuste", precision = 12, scale = 2)
    private BigDecimal valorAjuste;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Arredondamento arredondamento;

    @Column(name = "inicio_vigencia", nullable = false)
    private LocalDate inicioVigencia;

    @Column(name = "termino_vigencia")
    private LocalDate terminoVigencia;

    @Column(name = "valor_minimo_venda", precision = 12, scale = 2)
    private BigDecimal valorMinimoVenda;

    @Column(name = "percentual_comissao_padrao", precision = 5, scale = 2)
    private BigDecimal percentualComissaoPadrao;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @OneToMany(mappedBy = "tabelaPreco", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TabelaPrecoItem> itens = new ArrayList<>();
}
