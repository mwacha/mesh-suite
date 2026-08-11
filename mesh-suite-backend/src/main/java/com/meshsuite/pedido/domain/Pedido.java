package com.meshsuite.pedido.domain;

import com.meshsuite.partner.domain.Partner;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import com.meshsuite.user.domain.User;
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
@Table(name = "pedido")
@Getter
@Setter
public class Pedido {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private Integer numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Partner cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private User vendedor;

    @Column(name = "data_pedido", nullable = false)
    private LocalDate dataPedido = LocalDate.now();

    @Column(name = "data_entrega")
    private LocalDate dataEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusPedido status = StatusPedido.DIGITADO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemPedido> itens = new ArrayList<>();
}
